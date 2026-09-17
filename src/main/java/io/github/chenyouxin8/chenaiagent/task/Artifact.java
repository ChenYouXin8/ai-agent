package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

@Data
public class Artifact {
    private final String artifactId;
    private final String name;
    private final String type;
    private final String path;
    private final long createdAt = System.currentTimeMillis();

    public Artifact(String artifactId, String name, String type, String path) {
        this.artifactId = artifactId;
        this.name = name;
        this.type = type;
        this.path = path;
    }
}
