package io.github.chenyouxin8.chenaiagent.task;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskRepositoryTest {

    @Test
    void shouldPersistTaskStepsArtifactsReviewUsageAndApproval() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_repo_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask task = new ChenTask("task_test_001", "测试一个持久化任务");
        task.setTitle("持久化测试");
        task.setTenantId("tenant-a");
        task.setOwnerId("user-a");
        task.setSessionId("session-a");
        task.setPriority(TaskPriority.HIGH);
        task.setPlanSummary("测试计划");
        task.setActualInputTokens(1200);
        task.setActualOutputTokens(800);
        task.setModelCallCount(4);
        task.setEstimatedCost(0.1234);

        TaskStep step = new TaskStep("task_test_001_step_1", 1, "执行", "完成测试", true, List.of(), ApprovalStatus.PENDING);
        step.setStatus(StepStatus.PENDING);
        step.setApprovalNote("请确认发布");
        step.setRetryCount(1);
        step.setDurationMs(321);
        step.setActualInputTokens(1200);
        step.setActualOutputTokens(800);
        step.setModelCallCount(4);
        task.getSteps().add(step);

        Artifact artifact = new Artifact("artifact_001", "result.pdf", "pdf", "data/result.pdf");
        artifact.setVersion(2);
        artifact.setSizeBytes(9876);
        artifact.setMediaType("application/pdf");
        artifact.setChecksum("abc123");
        task.getArtifacts().add(artifact);
        task.setReview(new ReviewDecision(true, "通过", ""));

        repository.save(task);

        List<ChenTask> restored = repository.findAll();
        assertEquals(1, restored.size());
        ChenTask result = restored.get(0);
        assertEquals("tenant-a", result.getTenantId());
        assertEquals("user-a", result.getOwnerId());
        assertEquals("session-a", result.getSessionId());
        assertEquals(TaskPriority.HIGH, result.getPriority());
        assertEquals(1200, result.getActualInputTokens());
        assertEquals(800, result.getActualOutputTokens());
        assertEquals(4, result.getModelCallCount());
        assertEquals(1, result.getSteps().size());
        assertEquals(ApprovalStatus.PENDING, result.getSteps().get(0).getApprovalStatus());
        assertEquals("请确认发布", result.getSteps().get(0).getApprovalNote());
        assertTrue(result.getSteps().get(0).isParallelizable());
        assertEquals(321, result.getSteps().get(0).getDurationMs());
        assertEquals(1200, result.getSteps().get(0).getActualInputTokens());
        assertEquals(800, result.getSteps().get(0).getActualOutputTokens());
        assertEquals(4, result.getSteps().get(0).getModelCallCount());
        assertEquals(1, result.getArtifacts().size());
        assertEquals(2, result.getArtifacts().get(0).getVersion());
        assertEquals(9876, result.getArtifacts().get(0).getSizeBytes());
        assertEquals("application/pdf", result.getArtifacts().get(0).getMediaType());
        assertEquals("abc123", result.getArtifacts().get(0).getChecksum());
        assertNotNull(result.getReview());
        assertTrue(result.getReview().passed());
    }

    @Test
    void shouldPersistAndReplayTaskEvents() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_event_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));
        ChenTask task = new ChenTask("task_event_001", "event");
        repository.save(task);

        TaskEvent first = new TaskEvent(task.getTaskId(), TaskEventType.TASK_CREATED, null, "created");
        TaskEvent second = new TaskEvent(task.getTaskId(), TaskEventType.TASK_APPROVAL_REQUIRED, "step-1", "approve");
        repository.appendEvent(first);
        repository.appendEvent(second);

        List<TaskEvent> history = repository.findEvents(task.getTaskId(), 10);
        assertEquals(2, history.size());
        assertEquals(TaskEventType.TASK_CREATED, history.get(0).getType());
        assertEquals(TaskEventType.TASK_APPROVAL_REQUIRED, history.get(1).getType());
        assertEquals("approve", history.get(1).getMessage());
    }

    @Test
    void guardedSaveOnlyPersistsWhenExpectedStatusMatches() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_guarded_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask task = new ChenTask("task_guarded", "发布上线");
        task.setStatus(TaskStatus.WAITING_USER);
        TaskStep step = new TaskStep("task_guarded_step_1", 1, "发布", "发布到生产环境",
                false, List.of(), ApprovalStatus.PENDING);
        task.getSteps().add(step);
        repository.save(task);

        task.setStatus(TaskStatus.QUEUED);
        step.setApprovalStatus(ApprovalStatus.APPROVED);
        step.setApprovalNote("确认发布");
        assertThrows(IllegalStateException.class, () -> repository.save(task, TaskStatus.RUNNING));

        ChenTask untouched = repository.find("task_guarded");
        assertEquals(TaskStatus.WAITING_USER, untouched.getStatus());
        assertEquals(ApprovalStatus.PENDING, untouched.getSteps().get(0).getApprovalStatus());

        repository.save(task, TaskStatus.WAITING_USER);

        ChenTask approved = repository.find("task_guarded");
        assertEquals(TaskStatus.QUEUED, approved.getStatus());
        assertEquals(ApprovalStatus.APPROVED, approved.getSteps().get(0).getApprovalStatus());
        assertEquals("确认发布", approved.getSteps().get(0).getApprovalNote());
    }

    @Test
    void filteredEventHistoryAppliesFiltersBeforeLimit() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_audit_filter_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask task = new ChenTask("task_audit_filter", "audit");
        repository.save(task);

        // 一条时间戳最早、埋在 500 条更新事件之下的审批事件
        repository.appendEvent(new TaskEvent("e_old_approval", "task_audit_filter",
                TaskEventType.TASK_APPROVAL_GRANTED, "step-1", "actor=admin-1", 1000L));
        for (int i = 0; i < 500; i++) {
            repository.appendEvent(new TaskEvent("e_msg_" + i, "task_audit_filter",
                    TaskEventType.MESSAGE, null, "message " + i, 1100L + i));
        }

        // 类型过滤能触达 500 条之外的历史事件（旧实现先取最近 500 条会返回空）
        List<TaskEvent> approvals = repository.findEvents("task_audit_filter",
                new TaskAuditQuery(200, 0L, Long.MAX_VALUE, List.of(TaskEventType.TASK_APPROVAL_GRANTED), null));
        assertEquals(1, approvals.size());
        assertEquals("e_old_approval", approvals.get(0).getEventId());

        // stepId 过滤
        List<TaskEvent> stepEvents = repository.findEvents("task_audit_filter",
                new TaskAuditQuery(200, 0L, Long.MAX_VALUE, List.of(), "step-1"));
        assertEquals(1, stepEvents.size());
        assertEquals("e_old_approval", stepEvents.get(0).getEventId());

        // 时间窗口过滤（1200..1300 含端点共 101 条，升序返回）
        List<TaskEvent> windowed = repository.findEvents("task_audit_filter",
                new TaskAuditQuery(500, 1200L, 1300L, List.of(), null));
        assertEquals(101, windowed.size());
        assertEquals(1200L, windowed.get(0).getTimestamp());
        assertEquals(1300L, windowed.get(windowed.size() - 1).getTimestamp());

        // limit 取最近 N 条并以时间升序返回（消息时间戳 1100..1599）
        List<TaskEvent> limited = repository.findEvents("task_audit_filter",
                new TaskAuditQuery(3, 0L, Long.MAX_VALUE, List.of(), null));
        assertEquals(3, limited.size());
        assertEquals(1597L, limited.get(0).getTimestamp());
        assertEquals(1599L, limited.get(2).getTimestamp());
    }

    @Test
    void saveRollsBackTaskRowAndStepsWhenStepInsertFails() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_save_tx_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask task = new ChenTask("task_save_tx", "事务回滚");
        task.setStatus(TaskStatus.RUNNING);
        repository.save(task);

        ChenTask update = repository.find("task_save_tx");
        update.setStatus(TaskStatus.QUEUED);
        update.getSteps().add(new TaskStep("task_save_tx_dup", 1, "步骤A", "描述", false, List.of(), ApprovalStatus.NONE));
        update.getSteps().add(new TaskStep("task_save_tx_dup", 2, "步骤B", "描述", false, List.of(), ApprovalStatus.NONE));

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(DataIntegrityViolationException.class, () ->
                transaction.executeWithoutResult(status -> repository.save(update)));

        ChenTask after = repository.find("task_save_tx");
        assertEquals(TaskStatus.RUNNING, after.getStatus());
        assertTrue(after.getSteps().isEmpty());
    }

    @Test
    void saveMethodsAreTransactionalForAtomicAggregateWrites() throws Exception {
        Transactional plain = TaskRepository.class.getMethod("save", ChenTask.class).getAnnotation(Transactional.class);
        Transactional guarded = TaskRepository.class.getMethod("save", ChenTask.class, TaskStatus.class)
                .getAnnotation(Transactional.class);

        assertNotNull(plain);
        assertNotNull(guarded);
        assertEquals(Propagation.REQUIRED, plain.propagation());
        assertEquals(Propagation.REQUIRED, guarded.propagation());
    }

    @Test
    void sameMillisecondEventsKeepInsertionOrder() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_seq_order_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        repository.save(new ChenTask("task_seq_order", "顺序"));
        repository.appendEvent(new TaskEvent("e_t500", "task_seq_order", TaskEventType.MESSAGE, null, "先发生", 500L));
        repository.appendEvent(new TaskEvent("e_a", "task_seq_order", TaskEventType.MESSAGE, null, "同毫秒第一条", 1000L));
        repository.appendEvent(new TaskEvent("e_b", "task_seq_order", TaskEventType.MESSAGE, null, "同毫秒第二条", 1000L));
        repository.appendEvent(new TaskEvent("e_c", "task_seq_order", TaskEventType.MESSAGE, null, "同毫秒第三条", 1000L));
        repository.appendEvent(new TaskEvent("e_t1500", "task_seq_order", TaskEventType.MESSAGE, null, "最后发生", 1500L));

        List<String> expected = List.of("e_t500", "e_a", "e_b", "e_c", "e_t1500");
        assertEquals(expected, repository.findEvents("task_seq_order", 10).stream().map(TaskEvent::getEventId).toList());
        assertEquals(expected, repository.findEvents("task_seq_order",
                new TaskAuditQuery(500, 0L, Long.MAX_VALUE, List.of(), null)).stream().map(TaskEvent::getEventId).toList());

        // limit 取最近 N 条：t=1500 与同毫秒中最晚插入的 e_c
        assertEquals(List.of("e_c", "e_t1500"), repository.findEvents("task_seq_order", 2).stream()
                .map(TaskEvent::getEventId).toList());
    }

    @Test
    void deleteTaskWithEventsIsRejectedToPreserveAuditTrail() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_delete_restrict_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        repository.save(new ChenTask("task_restrict", "审计保留"));
        repository.appendEvent(new TaskEvent("e_keep", "task_restrict", TaskEventType.TASK_CREATED, null, "任务已创建", 1L));

        assertThrows(DataAccessException.class, () -> repository.delete("task_restrict"));
        assertNotNull(repository.find("task_restrict"));
    }

    @Test
    void shouldCountActiveTasksByTenant() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_quota_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask active = new ChenTask("task_active", "active");
        active.setTenantId("tenant-a");
        active.setStatus(TaskStatus.QUEUED);
        repository.save(active);

        ChenTask completed = new ChenTask("task_done", "done");
        completed.setTenantId("tenant-a");
        completed.setStatus(TaskStatus.COMPLETED);
        repository.save(completed);

        ChenTask otherTenant = new ChenTask("task_other", "other");
        otherTenant.setTenantId("tenant-b");
        otherTenant.setStatus(TaskStatus.RUNNING);
        repository.save(otherTenant);

        assertEquals(1, repository.countActiveTasks("tenant-a"));
        assertEquals(1, repository.countActiveTasks("tenant-b"));
        assertEquals(0, repository.countActiveTasks("tenant-c"));
    }
}
