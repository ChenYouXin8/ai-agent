package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactAccessServiceTest {

    @Test
    void resolvesFilesInsideConfiguredRoot(@TempDir Path root) throws Exception {
        Path artifactFile = root.resolve("report.txt");
        Files.writeString(artifactFile, "hello", StandardCharsets.UTF_8);

        ArtifactAccessService service = new ArtifactAccessService(root.toString());
        Artifact artifact = new Artifact("artifact-1", "report.txt", "txt", artifactFile.toString());

        assertTrue(service.resolve(artifact).exists());
    }

    @Test
    void rejectsFilesOutsideConfiguredRoot(@TempDir Path root) throws Exception {
        Path outside = Files.createTempFile("chenmanus-outside", ".txt");
        ArtifactAccessService service = new ArtifactAccessService(root.toString());
        Artifact artifact = new Artifact("artifact-2", "outside.txt", "txt", outside.toString());

        try {
            assertThrows(ResponseStatusException.class, () -> service.resolve(artifact));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
