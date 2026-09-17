package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TaskRepository {

    private static final String TASK_COLUMNS = """
            task_id, prompt, title, status, created_at, updated_at,
            started_at, completed_at, duration_ms,
            estimated_input_tokens, estimated_output_tokens,
            actual_input_tokens, actual_output_tokens, model_call_count, estimated_cost,
            result, error, plan_summary, tenant_id, owner_id, session_id, priority,
            review_passed, review_feedback, review_missing_items
            """;

    private static final String TASK_UPDATE = """
            UPDATE chen_tasks SET prompt=?, title=?, status=?, updated_at=?,
                started_at=?, completed_at=?, duration_ms=?,
                estimated_input_tokens=?, estimated_output_tokens=?,
                actual_input_tokens=?, actual_output_tokens=?, model_call_count=?, estimated_cost=?,
                result=?, error=?, plan_summary=?, tenant_id=?, owner_id=?, session_id=?, priority=?,
                review_passed=?, review_feedback=?, review_missing_items=?
            WHERE task_id=?
            """;

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChenTask> findAll() {
        List<ChenTask> tasks = jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM chen_tasks ORDER BY created_at DESC",
                taskRowMapper());
        for (ChenTask task : tasks) loadChildren(task);
        return tasks;
    }

    public ChenTask find(String taskId) {
        List<ChenTask> tasks = jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM chen_tasks WHERE task_id = ?",
                taskRowMapper(), taskId);
        if (tasks.isEmpty()) return null;
        ChenTask task = tasks.get(0);
        loadChildren(task);
        return task;
    }

    public List<TaskEvent> findEvents(String taskId, int limit) {
        int max = Math.max(1, Math.min(limit, 500));
        List<TaskEvent> events = jdbc.query("""
                SELECT event_id, task_id, type, step_id, message, created_at
                FROM chen_task_events
                WHERE task_id = ?
                ORDER BY created_at DESC, seq DESC
                LIMIT ?
                """, taskEventRowMapper(), taskId, max);
        Collections.reverse(events);
        return events;
    }

    // 审计查询：过滤条件必须先于 LIMIT 在数据库执行；若先取最近 500 条再内存过滤，
    // 更早的历史事件（如埋在大量工具事件下的审批记录）将永远无法检索
    public List<TaskEvent> findEvents(String taskId, TaskAuditQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, task_id, type, step_id, message, created_at
                FROM chen_task_events
                WHERE task_id = ? AND created_at >= ? AND created_at <= ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(taskId);
        params.add(query.from());
        params.add(query.to());
        if (!query.types().isEmpty()) {
            sql.append(" AND type IN (")
                    .append(String.join(",", Collections.nCopies(query.types().size(), "?")))
                    .append(")");
            for (TaskEventType type : query.types()) params.add(type.name());
        }
        if (query.stepId() != null && !query.stepId().isBlank()) {
            sql.append(" AND step_id = ?");
            params.add(query.stepId());
        }
        // seq 为自增次序键：同毫秒事件按插入顺序返回，时间戳不再是唯一的排序依据
        sql.append(" ORDER BY created_at DESC, seq DESC LIMIT ?");
        params.add(query.limit());

        List<TaskEvent> events = jdbc.query(sql.toString(), taskEventRowMapper(), params.toArray());
        Collections.reverse(events);
        return events;
    }

    public int countActiveTasks(String tenantId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM chen_tasks
                WHERE tenant_id = ?
                  AND status IN ('QUEUED', 'PLANNING', 'RUNNING', 'PAUSED', 'WAITING_USER', 'REVIEWING')
                """, Integer.class, tenantId);
        return count == null ? 0 : count;
    }

    // 单事务写入整个聚合（任务行 + 步骤 + 产物）：任务行 UPDATE 总是先于子表 DELETE/INSERT 执行并持有行锁，
    // 跨实例并发保存同一任务时在数据库层串行化，步骤不会交错覆盖或重复；已有事务（如审批）则加入该事务
    @Transactional
    public void save(ChenTask task) {
        int updated = jdbc.update(TASK_UPDATE, taskUpdateParams(task));
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO chen_tasks (
                        task_id, prompt, title, status, created_at, updated_at,
                        started_at, completed_at, duration_ms,
                        estimated_input_tokens, estimated_output_tokens,
                        actual_input_tokens, actual_output_tokens, model_call_count, estimated_cost,
                        result, error, plan_summary, tenant_id, owner_id, session_id, priority,
                        review_passed, review_feedback, review_missing_items
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.getTaskId(), task.getPrompt(), task.getTitle(), task.getStatus().name(),
                    task.getCreatedAt(), task.getUpdatedAt(), task.getStartedAt(), task.getCompletedAt(), task.getDurationMs(),
                    task.getEstimatedInputTokens(), task.getEstimatedOutputTokens(),
                    task.getActualInputTokens(), task.getActualOutputTokens(), task.getModelCallCount(), task.getEstimatedCost(),
                    task.getResult(), task.getError(), task.getPlanSummary(), task.getTenantId(), task.getOwnerId(),
                    task.getSessionId(), task.getPriority().name(),
                    task.getReview() == null ? null : task.getReview().passed(),
                    task.getReview() == null ? null : task.getReview().feedback(),
                    task.getReview() == null ? null : task.getReview().missingItems());
        }
        writeChildren(task);
    }

    // 条件更新（CAS）：仅当数据库中任务仍处于 expectedStatus 才写入；0 行更新说明状态已被并发修改，整次保存拒绝并回滚
    @Transactional
    public void save(ChenTask task, TaskStatus expectedStatus) {
        Object[] base = taskUpdateParams(task);
        Object[] params = Arrays.copyOf(base, base.length + 1);
        params[base.length] = expectedStatus.name();
        int updated = jdbc.update(TASK_UPDATE + " AND status=?", params);
        if (updated == 0) {
            throw new IllegalStateException("任务状态已被并发修改，本次操作未生效：" + task.getTaskId());
        }
        writeChildren(task);
    }

    private void writeChildren(ChenTask task) {
        jdbc.update("DELETE FROM chen_task_steps WHERE task_id = ?", task.getTaskId());
        for (TaskStep step : task.getSteps()) {
            jdbc.update("""
                    INSERT INTO chen_task_steps (
                        step_id, task_id, seq, title, description, status,
                        output, error, started_at, completed_at, duration_ms,
                        retry_count, parallelizable, depends_on, approval_status, approval_note,
                        estimated_input_tokens, estimated_output_tokens,
                        actual_input_tokens, actual_output_tokens, model_call_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    step.getStepId(), task.getTaskId(), step.getSequence(), step.getTitle(), step.getDescription(),
                    step.getStatus().name(), step.getOutput(), step.getError(), step.getStartedAt(),
                    step.getCompletedAt(), step.getDurationMs(), step.getRetryCount(), step.isParallelizable(),
                    encodeDependencies(step.getDependsOn()), step.getApprovalStatus().name(), step.getApprovalNote(),
                    step.getEstimatedInputTokens(), step.getEstimatedOutputTokens(),
                    step.getActualInputTokens(), step.getActualOutputTokens(), step.getModelCallCount());
        }

        jdbc.update("DELETE FROM chen_task_artifacts WHERE task_id = ?", task.getTaskId());
        for (Artifact artifact : task.getArtifacts()) {
            jdbc.update("""
                    INSERT INTO chen_task_artifacts (
                        artifact_id, task_id, name, type, path, created_at,
                        version, size_bytes, media_type, checksum
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifact.getArtifactId(), task.getTaskId(), artifact.getName(), artifact.getType(),
                    artifact.getPath(), artifact.getCreatedAt(), artifact.getVersion(), artifact.getSizeBytes(),
                    artifact.getMediaType(), artifact.getChecksum());
        }
    }

    private Object[] taskUpdateParams(ChenTask task) {
        return new Object[]{
                task.getPrompt(), task.getTitle(), task.getStatus().name(), task.getUpdatedAt(),
                task.getStartedAt(), task.getCompletedAt(), task.getDurationMs(),
                task.getEstimatedInputTokens(), task.getEstimatedOutputTokens(),
                task.getActualInputTokens(), task.getActualOutputTokens(), task.getModelCallCount(), task.getEstimatedCost(),
                task.getResult(), task.getError(), task.getPlanSummary(), task.getTenantId(), task.getOwnerId(),
                task.getSessionId(), task.getPriority().name(),
                task.getReview() == null ? null : task.getReview().passed(),
                task.getReview() == null ? null : task.getReview().feedback(),
                task.getReview() == null ? null : task.getReview().missingItems(),
                task.getTaskId()
        };
    }

    public void appendEvent(TaskEvent event) {
        jdbc.update("""
                INSERT INTO chen_task_events (event_id, task_id, type, step_id, message, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                event.getEventId(), event.getTaskId(), event.getType().name(), event.getStepId(),
                event.getMessage(), event.getTimestamp());
    }

    public void delete(String taskId) {
        jdbc.update("DELETE FROM chen_tasks WHERE task_id = ?", taskId);
    }

    private void loadChildren(ChenTask task) {
        task.getSteps().addAll(jdbc.query("""
                SELECT step_id, seq, title, description, status, output, error,
                       started_at, completed_at, duration_ms, retry_count,
                       parallelizable, depends_on, approval_status, approval_note,
                       estimated_input_tokens, estimated_output_tokens,
                       actual_input_tokens, actual_output_tokens, model_call_count
                FROM chen_task_steps WHERE task_id = ? ORDER BY seq
                """, taskStepRowMapper(), task.getTaskId()));

        task.getArtifacts().addAll(jdbc.query("""
                SELECT artifact_id, name, type, path, created_at, version, size_bytes, media_type, checksum
                FROM chen_task_artifacts WHERE task_id = ? ORDER BY created_at
                """, artifactRowMapper(), task.getTaskId()));
    }

    private RowMapper<ChenTask> taskRowMapper() {
        return (rs, rowNum) -> {
            ChenTask task = new ChenTask(rs.getString("task_id"), rs.getString("prompt"));
            task.setTitle(rs.getString("title"));
            task.setStatus(TaskStatus.valueOf(rs.getString("status")));
            task.setCreatedAt(rs.getLong("created_at"));
            task.setUpdatedAt(rs.getLong("updated_at"));
            task.setStartedAt(rs.getLong("started_at"));
            task.setCompletedAt(rs.getLong("completed_at"));
            task.setDurationMs(rs.getLong("duration_ms"));
            task.setEstimatedInputTokens(rs.getLong("estimated_input_tokens"));
            task.setEstimatedOutputTokens(rs.getLong("estimated_output_tokens"));
            task.setActualInputTokens(rs.getLong("actual_input_tokens"));
            task.setActualOutputTokens(rs.getLong("actual_output_tokens"));
            task.setModelCallCount(rs.getLong("model_call_count"));
            task.setEstimatedCost(rs.getDouble("estimated_cost"));
            task.setResult(rs.getString("result"));
            task.setError(rs.getString("error"));
            task.setPlanSummary(rs.getString("plan_summary"));
            task.setTenantId(rs.getString("tenant_id"));
            task.setOwnerId(rs.getString("owner_id"));
            task.setSessionId(rs.getString("session_id"));
            task.setPriority(TaskPriority.from(rs.getString("priority")));
            boolean reviewPassed = rs.getBoolean("review_passed");
            if (!rs.wasNull()) {
                task.setReview(new ReviewDecision(reviewPassed, rs.getString("review_feedback"), rs.getString("review_missing_items")));
            }
            return task;
        };
    }

    private RowMapper<TaskStep> taskStepRowMapper() {
        return (rs, rowNum) -> {
            TaskStep step = new TaskStep(
                    rs.getString("step_id"), rs.getInt("seq"), rs.getString("title"),
                    rs.getString("description"), rs.getBoolean("parallelizable"), decodeDependencies(rs.getString("depends_on")),
                    ApprovalStatus.valueOf(rs.getString("approval_status")));
            step.setStatus(StepStatus.valueOf(rs.getString("status")));
            step.setOutput(rs.getString("output"));
            step.setError(rs.getString("error"));
            step.setStartedAt(rs.getLong("started_at"));
            step.setCompletedAt(rs.getLong("completed_at"));
            step.setDurationMs(rs.getLong("duration_ms"));
            step.setRetryCount(rs.getInt("retry_count"));
            step.setApprovalNote(rs.getString("approval_note"));
            step.setEstimatedInputTokens(rs.getLong("estimated_input_tokens"));
            step.setEstimatedOutputTokens(rs.getLong("estimated_output_tokens"));
            step.setActualInputTokens(rs.getLong("actual_input_tokens"));
            step.setActualOutputTokens(rs.getLong("actual_output_tokens"));
            step.setModelCallCount(rs.getLong("model_call_count"));
            return step;
        };
    }

    private RowMapper<Artifact> artifactRowMapper() {
        return (rs, rowNum) -> {
            Artifact artifact = new Artifact(
                    rs.getString("artifact_id"), rs.getString("name"), rs.getString("type"),
                    rs.getString("path"), rs.getLong("created_at"));
            artifact.setVersion(rs.getInt("version"));
            artifact.setSizeBytes(rs.getLong("size_bytes"));
            artifact.setMediaType(rs.getString("media_type"));
            artifact.setChecksum(rs.getString("checksum"));
            return artifact;
        };
    }

    private RowMapper<TaskEvent> taskEventRowMapper() {
        return (rs, rowNum) -> new TaskEvent(
                rs.getString("event_id"),
                rs.getString("task_id"),
                TaskEventType.valueOf(rs.getString("type")),
                rs.getString("step_id"),
                rs.getString("message"),
                rs.getLong("created_at"));
    }

    private String encodeDependencies(List<Integer> dependencies) {
        return dependencies == null ? "" : dependencies.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Integer> decodeDependencies(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(Integer::valueOf)
                .toList();
    }
}
