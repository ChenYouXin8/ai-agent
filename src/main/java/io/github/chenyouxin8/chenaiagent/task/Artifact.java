package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;

@Data
public class Artifact implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String artifactId;
    private final String name;
    private final String type;
    private final String path;
    private final long createdAt;
    private int version = 1;
    private long sizeBytes;
    private String mediaType = "application/octet-stream";

    public Artifact(String artifactId, String name, String type, String path) {
        this(artifactId, name, type, path, System.currentTimeMillis());
    }

    public Artifact(String artifactId, String name, String type, String path, long createdAt) {
        this.artifactId = artifactId;
        this.name = name;
        this.type = type;
        this.path = path;
        this.createdAt = createdAt;
    }
}
