package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.task.Artifact;
import io.github.chenyouxin8.chenaiagent.task.ChenTask;
import io.github.chenyouxin8.chenaiagent.task.TaskManager;
import io.github.chenyouxin8.chenaiagent.task.TaskScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/tasks/{taskId}/artifacts")
public class ArtifactController {

    private final TaskManager taskManager;
    private final TaskScopeService scopeService;
    private final Path allowedRoot;

    public ArtifactController(
            TaskManager taskManager,
            TaskScopeService scopeService,
            @Value("${chenmanus.artifacts.allowed-root:.}") String allowedRoot
    ) {
        this.taskManager = taskManager;
        this.scopeService = scopeService;
        this.allowedRoot = Paths.get(allowedRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String taskId,
            @PathVariable String artifactId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId
    ) {
        ChenTask task = authorizedTask(taskId, tenantId, userId);
        Artifact artifact = findArtifact(task, artifactId);
        return buildResponse(artifact, resolveSafePath(artifact.getPath()), ContentDisposition.attachment());
    }

    @GetMapping("/{artifactId}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable String taskId,
            @PathVariable String artifactId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId
    ) {
        ChenTask task = authorizedTask(taskId, tenantId, userId);
        Artifact artifact = findArtifact(task, artifactId);
        Path file = resolveSafePath(artifact.getPath());
        MediaType mediaType = parseMediaType(artifact.getMediaType());
        if (!(mediaType.equals(MediaType.APPLICATION_PDF)
                || mediaType.getType().equals("image")
                || mediaType.getType().equals("text"))) {
            throw new ResponseStatusException(NOT_FOUND, "该产物类型不支持浏览器预览，请下载后打开");
        }
        return buildResponse(artifact, file, ContentDisposition.inline());
    }

    private ChenTask authorizedTask(String taskId, String tenantId, String userId) {
        ChenTask task = taskManager.get(taskId);
        scopeService.assertAccess(task, tenantId, userId);
        return task;
    }

    private Artifact findArtifact(ChenTask task, String artifactId) {
        return task.getArtifacts().stream()
                .filter(candidate -> Objects.equals(candidate.getArtifactId(), artifactId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "产物不存在"));
    }

    private ResponseEntity<Resource> buildResponse(
            Artifact artifact,
            Path file,
            ContentDisposition disposition
    ) {
        Resource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(artifact.getMediaType()));
        headers.setContentLength(artifact.getSizeBytes() > 0 ? artifact.getSizeBytes() : fileSize(file));
        headers.setContentDisposition(disposition.filename(artifact.getName()).build());
        headers.set("X-Artifact-Version", String.valueOf(artifact.getVersion()));
        if (artifact.getChecksum() != null && !artifact.getChecksum().isBlank()) {
            headers.set("X-Artifact-SHA256", artifact.getChecksum());
        }
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private Path resolveSafePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) throw new ResponseStatusException(NOT_FOUND, "产物不存在");
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
            throw new ResponseStatusException(NOT_FOUND, "仅支持本地交付产物");
        }
        try {
            Path candidate = Path.of(rawPath).toAbsolutePath().normalize();
            if (!candidate.startsWith(allowedRoot) || !Files.isRegularFile(candidate)) {
                throw new ResponseStatusException(NOT_FOUND, "产物不可访问");
            }
            return candidate;
        } catch (InvalidPathException e) {
            throw new ResponseStatusException(NOT_FOUND, "产物路径无效");
        }
    }

    private MediaType parseMediaType(String value) {
        try {
            return value == null || value.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
