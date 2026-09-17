package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class TaskRepository {

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChenTask> findAll() {
        List<ChenTask> tasks = jdbc.query("""
                SELECT task_id, prompt, title, status, created_at, updated_at,
                       started_at, completed_at, duration_ms,
                       estimated_input_tokens, estimated_output_tokens, estimated_cost,
                       result, error, plan_summary, tenant_id, owner_id, session_id, priority,
                       review_passed, review_feedback, review_missing_items
                FROM chen_tasks ORDER BY created_at DESC
                """, taskRowMapper());
        for (ChenTask task : tasks) loadChildren(task);
        return tasks;
    }

    public synchronized void save(ChenTask task) {
        int updated = jdbc.update("""
                UPDATE chen_tasks SET prompt=?, title=?, status=?, updated_at=?,
                    started_at=?, completed_at=?, duration_ms=?,
                    estimated_input_tokens=?, estimated_output_tokens=?, estimated_cost=?,
                    result=?, error=?, plan_summary=?, tenant_id=?, owner_id=?, session_id=?, priority=?,
                    review_passed=?, review_feedback=?, review_missing_items=?
                WHERE task_id=?
                """,
                task.getPrompt(), task.getTitle(), task.getStatus().name(), task.getUpdatedAt(),
                task.getStartedAt(), task.getCompletedAt(), task.getDurationMs(),
                task.getEstimatedInputTokens(), task.getEstimatedOutputTokens(), task.getEstimatedCost(),
                task.getResult(), task.getError(), task.getPlanSummary(), task.getTenantId(), task.getOwnerId(),
                task.getSessionId(), task.getPriority().name(),
                task.getReview() == null ? null : task.getReview().passed(),
                task.getReview() == null ? null : task.getReview().feedback(),
                task.getReview() == null ? null : task.getReview().missingItems(),
                task.getTaskId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO chen_tasks (
                        task_id, prompt, title, status, created_at, updated_at,
                        started_at, completed_at, duration_ms,
                        estimated_input_tokens, estimated_output_tokens, estimated_cost,
                        result, error, plan_summary, tenant_id, owner_id, session_id, priority,
                        review_passed, review_feedback, review_missing_items
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    task.getTaskId(), task.getPrompt(), task.getTitle(), task.getStatus().name(),
                    task.getCreatedAt(), task.getUpdatedAt(), task.getStartedAt(), task.getCompletedAt(), task.getDurationMs(),
                    task.getEstimatedInputTokens(), task.getEstimatedOutputTokens(), task.getEstimatedCost(),
                    task.getResult(), task.getError(), task.getPlanSummary(), task.getTenantId(), task.getOwnerId(),
                    task.getSessionId(), task.getPriority().name(),
                    task.getReview() == null ? null : task.getReview().passed(),
                    task.getReview() == null ? null : task.getReview().feedback(),
                    task.getReview() == null ? null : task.getReview().missingItems());
        }

        jdbc.update("DELETE FROM chen_task_steps WHERE task_id = ?", task.getTaskId());
        for (TaskStep step : task.getSteps()) {
            jdbc.update("""
                    INSERT INTO chen_task_steps (
                        step_id, task_id, seq, title, description, status,
                        output, error, started_at, completed_at, duration_ms,
                        retry_count, parallelizable, depends_on,
                        estimated_input_tokens, estimated_output_tokens
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    step.getStepId(), task.getTaskId(), step.getSequence(), step.getTitle(), step.getDescription(),
                    step.getStatus().name(), step.getOutput(), step.getError(), step.getStartedAt(),
                    step.getCompletedAt(), step.getDurationMs(), step.getRetryCount(), step.isParallelizable(),
                    encodeDependencies(step.getDependsOn()), step.getEstimatedInputTokens(), step.getEstimatedOutputTokens());
        }

        jdbc.update("DELETE FROM chen_task_artifacts WHERE task_id = ?", task.getTaskId());
        for (Artifact artifact : task.getArtifacts()) {
            jdbc.update("""
                    INSERT INTO chen_task_artifacts (artifact_id, task_id, name, type, path, created_at, version, size_bytes, media_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifact.getArtifactId(), task.getTaskId(), artifact.getName(), artifact.getType(),
                    artifact.getPath(), artifact.getCreatedAt(), artifact.getVersion(), artifact.getSizeBytes(), artifact.getMediaType());
        }
    }

    public void delete(String taskId) {
        jdbc.update("DELETE FROM chen_tasks WHERE task_id = ?", taskId);
    }

    private void loadChildren(ChenTask task) {
        task.getSteps().addAll(jdbc.query("""
                SELECT step_id, seq, title, description, status, output, error,
                       started_at, completed_at, duration_ms, retry_count,
                       parallelizable, depends_on, estimated_input_tokens, estimated_output_tokens
                FROM chen_task_steps WHERE task_id = ? ORDER BY seq
                """, taskStepRowMapper(), task.getTaskId()));

        task.getArtifacts().addAll(jdbc.query("""
                SELECT artifact_id, name, type, path, created_at, version, size_bytes, media_type
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
                    rs.getString("description"), rs.getBoolean("parallelizable"), decodeDependencies(rs.getString("depends_on")));
            step.setStatus(StepStatus.valueOf(rs.getString("status")));
            step.setOutput(rs.getString("output"));
            step.setError(rs.getString("error"));
            step.setStartedAt(rs.getLong("started_at"));
            step.setCompletedAt(rs.getLong("completed_at"));
            step.setDurationMs(rs.getLong("duration_ms"));
            step.setRetryCount(rs.getInt("retry_count"));
            step.setEstimatedInputTokens(rs.getLong("estimated_input_tokens"));
            step.setEstimatedOutputTokens(rs.getLong("estimated_output_tokens"));
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
            return artifact;
        };
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
