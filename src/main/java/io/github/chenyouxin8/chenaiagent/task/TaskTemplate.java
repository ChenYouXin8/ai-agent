package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskTemplate implements Serializable {
    private final String templateId;
    private final String name;
    private final String promptTemplate;
    private final String tenantId;
    private final String ownerId;
    private final TaskPriority priority;
    private final boolean approvalRequired;
    private boolean enabled = true;
    private final long createdAt = System.currentTimeMillis();
    private long updatedAt = createdAt;

    public TaskTemplate(String templateId, String name, String promptTemplate, String tenantId,
                        String ownerId, TaskPriority priority, boolean approvalRequired) {
        this.templateId = templateId;
        this.name = name;
        this.promptTemplate = promptTemplate;
        this.tenantId = tenantId;
        this.ownerId = ownerId;
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.approvalRequired = approvalRequired;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
