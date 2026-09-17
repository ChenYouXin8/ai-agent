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
    void shouldPersistTaskStepsArtifactsReviewAndUsage() {
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

        TaskStep step = new TaskStep("task_test_001_step_1", 1, "执行", "完成测试", true, List.of());
        step.setStatus(StepStatus.COMPLETED);
        step.setOutput("执行结果");
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
        assertEquals("执行结果", result.getSteps().get(0).getOutput());
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
