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
    void shouldPersistTaskStepsArtifactsAndReview() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:task_repo_test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        TaskRepository repository = new TaskRepository(new JdbcTemplate(dataSource));

        ChenTask task = new ChenTask("task_test_001", "测试一个持久化任务");
        task.setTitle("持久化测试");
        task.setOwnerId("user-a");
        task.setSessionId("session-a");
        task.setPlanSummary("测试计划");

        TaskStep step = new TaskStep("task_test_001_step_1", 1, "执行", "完成测试");
        step.setStatus(StepStatus.COMPLETED);
        step.setOutput("执行结果");
        step.setRetryCount(1);
        task.getSteps().add(step);

        task.getArtifacts().add(new Artifact("artifact_001", "result.pdf", "pdf", "data/result.pdf"));
        task.setReview(new ReviewDecision(true, "通过", ""));

        repository.save(task);

        List<ChenTask> restored = repository.findAll();
        assertEquals(1, restored.size());
        ChenTask result = restored.get(0);
        assertEquals("user-a", result.getOwnerId());
        assertEquals("session-a", result.getSessionId());
        assertEquals(1, result.getSteps().size());
        assertEquals("执行结果", result.getSteps().get(0).getOutput());
        assertEquals(1, result.getArtifacts().size());
        assertEquals("result.pdf", result.getArtifacts().get(0).getName());
        assertNotNull(result.getReview());
        assertTrue(result.getReview().passed());
    }
}
