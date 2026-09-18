package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ArtifactAccessService {

    private final Path root;

    public ArtifactAccessService(
            @Value("${chenmanus.artifacts.root:./data}") String root
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public Artifact find(ChenTask task, String artifactId) {
        return task.getArtifacts().stream()
                .filter(artifact -> artifact.getArtifactId().equals(artifactId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "产物不存在"));
    }

    public Resource resolve(Artifact artifact) {
        String path = artifact.getPath();
        if (path == null || path.isBlank() || path.startsWith("http://") || path.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该产物没有可直接下载的本地文件");
        }

        try {
            Path realRoot = root.toRealPath();
            Path candidate = Path.of(path).toAbsolutePath().normalize();
            if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "产物文件不存在");
            }
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "产物文件不在允许的目录内");
            }
            return new FileSystemResource(realCandidate);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取产物文件");
        }
    }
}
