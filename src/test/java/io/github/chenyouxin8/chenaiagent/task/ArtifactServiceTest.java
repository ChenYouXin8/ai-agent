package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ArtifactServiceTest {

    @Test
    void detectsWindowsAndUnixStylePathsInOutput() {
        ChenTask task = new ChenTask("task_artifact_paths", "paths");
        TaskManager taskManager = mock(TaskManager.class);
        ArtifactService service = new ArtifactService();

        service.capture(task, "生成 C:\\reports\\summary.pdf, /var/log/run.csv, ./data/result.xlsx", taskManager);

        List<String> paths = task.getArtifacts().stream().map(Artifact::getPath).toList();
        assertEquals(List.of("C:\\reports\\summary.pdf", "/var/log/run.csv", "./data/result.xlsx"), paths);
    }

    @Test
    void plainUrlDoesNotCreateGhostPathArtifact() {
        ChenTask task = new ChenTask("task_artifact_url", "url");
        TaskManager taskManager = mock(TaskManager.class);
        ArtifactService service = new ArtifactService();

        service.capture(task, "下载 https://example.com/report.pdf 与本地 /data/local.csv", taskManager);

        List<String> paths = task.getArtifacts().stream().map(Artifact::getPath).toList();
        assertEquals(List.of("/data/local.csv"), paths);
    }

    @Test
    void markdownLinkUrlIsCapturedOnceWithoutGhost() {
        ChenTask task = new ChenTask("task_artifact_markdown", "markdown");
        TaskManager taskManager = mock(TaskManager.class);
        ArtifactService service = new ArtifactService();

        service.capture(task, "见 [报告](https://example.com/report.pdf) 获取详情", taskManager);

        List<String> paths = task.getArtifacts().stream().map(Artifact::getPath).toList();
        assertEquals(List.of("https://example.com/report.pdf"), paths);
    }

    @Test
    void urlWithPortDoesNotCreateGhostPathArtifact() {
        ChenTask task = new ChenTask("task_artifact_port", "port");
        TaskManager taskManager = mock(TaskManager.class);
        ArtifactService service = new ArtifactService();

        service.capture(task, "预览 http://localhost:8080/report.pdf 完成", taskManager);

        assertEquals(0, task.getArtifacts().size());
    }

    @Test
    void shouldCreateChecksumAndBumpVersionWhenArtifactContentChanges() throws Exception {
        Path file = Files.createTempFile("chenmanus-artifact-", ".txt");
        try {
            Files.writeString(file, "version-one");
            ChenTask task = new ChenTask("task_artifact_test", "artifact");
            TaskManager taskManager = mock(TaskManager.class);
            ArtifactService service = new ArtifactService();

            service.capture(task, file.toString(), taskManager);
            assertEquals(1, task.getArtifacts().size());
            Artifact artifact = task.getArtifacts().get(0);
            assertEquals(1, artifact.getVersion());
            assertNotNull(artifact.getChecksum());

            Files.writeString(file, "version-two");
            service.capture(task, file.toString(), taskManager);

            assertEquals(1, task.getArtifacts().size());
            assertEquals(2, task.getArtifacts().get(0).getVersion());
            assertNotNull(task.getArtifacts().get(0).getChecksum());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
