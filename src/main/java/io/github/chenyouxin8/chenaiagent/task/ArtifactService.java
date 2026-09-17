package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArtifactService {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)\\]\\(([^)]+\\.(pdf|docx|xlsx|csv|png|jpg|jpeg|zip|txt))\\)", Pattern.CASE_INSENSITIVE);
    // [A-Za-z]:[\\/] 匹配 Windows 盘符绝对路径（C:\ 或 C:/）——否则 Windows 上的绝对路径工具输出无法被捕获为 Artifact
    private static final Pattern PATH = Pattern.compile("(?<!https?://)(?:[A-Za-z]:[\\\\/]|\\./|data/|/)[^\\s)]+\\.(pdf|docx|xlsx|csv|png|jpg|jpeg|zip|txt)", Pattern.CASE_INSENSITIVE);

    public void capture(ChenTask task, String output, TaskManager taskManager) {
        if (output == null || output.isBlank()) return;

        Matcher markdown = MARKDOWN_LINK.matcher(output);
        while (markdown.find()) {
            addArtifact(task, markdown.group(1), markdown.group(2), taskManager);
        }

        Matcher paths = PATH.matcher(output);
        while (paths.find()) {
            String path = paths.group();
            String name = Path.of(path).getFileName() == null ? path : Path.of(path).getFileName().toString();
            addArtifact(task, name, path, taskManager);
        }
    }

    private void addArtifact(ChenTask task, String name, String path, TaskManager taskManager) {
        Artifact metadata = new Artifact(
                "artifact_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                name,
                extension(path),
                path
        );
        enrichMetadata(metadata);

        Artifact existing = task.getArtifacts().stream()
                .filter(candidate -> candidate.getPath().equals(path))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (existing.getChecksum() == null || !existing.getChecksum().equals(metadata.getChecksum())) {
                existing.setVersion(existing.getVersion() + 1);
                existing.setSizeBytes(metadata.getSizeBytes());
                existing.setMediaType(metadata.getMediaType());
                existing.setChecksum(metadata.getChecksum());
                task.touch();
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.ARTIFACT_CREATED, null,
                        name + " → v" + existing.getVersion() + "（内容已更新）"));
            }
            return;
        }

        task.getArtifacts().add(metadata);
        task.touch();
        taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.ARTIFACT_CREATED, null,
                name + " → " + path));
    }

    private String extension(String path) {
        if (path == null || !path.contains(".")) return "file";
        return path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void enrichMetadata(Artifact artifact) {
        if (artifact.getPath().startsWith("http://") || artifact.getPath().startsWith("https://")) return;
        try {
            Path file = Path.of(artifact.getPath()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) return;
            artifact.setSizeBytes(Files.size(file));
            String detected = Files.probeContentType(file);
            if (detected != null && !detected.isBlank()) artifact.setMediaType(detected);
            artifact.setChecksum(sha256(file));
        } catch (Exception ignored) {
            // Metadata is best-effort; the artifact path remains usable for later safe resolution.
        }
    }

    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            return null;
        }
    }
}
