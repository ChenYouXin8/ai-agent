package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArtifactService {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)\\]\\(([^)]+\\.(pdf|docx|xlsx|csv|png|jpg|jpeg|zip|txt))\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH = Pattern.compile("(?<!https?://)(?:\\./|data/|/)[^\\s)]+\\.(pdf|docx|xlsx|csv|png|jpg|jpeg|zip|txt)", Pattern.CASE_INSENSITIVE);

    public void capture(ChenTask task, String output, TaskManager taskManager) {
        if (output == null || output.isBlank()) return;

        Matcher markdown = MARKDOWN_LINK.matcher(output);
        while (markdown.find()) {
            addArtifact(task, markdown.group(1), markdown.group(2), taskManager);
        }

        Matcher paths = PATH.matcher(output);
        while (paths.find()) {
            String path = paths.group();
            String name = path.substring(path.lastIndexOf('/') + 1);
            addArtifact(task, name, path, taskManager);
        }
    }

    private void addArtifact(ChenTask task, String name, String path, TaskManager taskManager) {
        boolean duplicate = task.getArtifacts().stream().anyMatch(existing -> existing.getPath().equals(path));
        if (duplicate) return;

        String extension = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "file";
        Artifact artifact = new Artifact(
                "artifact_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                name,
                extension,
                path
        );
        task.getArtifacts().add(artifact);
        task.touch();
        taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.ARTIFACT_CREATED, null, name + " → " + path));
    }
}
