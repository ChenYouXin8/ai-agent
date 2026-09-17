package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ArtifactServiceTest {

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
