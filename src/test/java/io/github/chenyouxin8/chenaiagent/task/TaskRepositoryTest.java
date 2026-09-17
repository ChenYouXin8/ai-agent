package io.github.chenyouxin8.chenaiagent.task;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

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
