package io.github.chenyouxin8.chenaiagent.task;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskSchedule implements Serializable {
    private final String scheduleId;
    private final String templateId;
    private final String tenantId;
    private final String ownerId;
    private final String cron;
    private final String timezone;
    private boolean enabled = true;
    private long nextRunAt;
    private long lastRunAt;
    private final long createdAt = System.currentTimeMillis();
    private long updatedAt = createdAt;

    public TaskSchedule(String scheduleId, String templateId, String tenantId, String ownerId,
                        String cron, String timezone, long nextRunAt) {
        this.scheduleId = scheduleId;
        this.templateId = templateId;
        this.tenantId = tenantId;
        this.ownerId = ownerId;
        this.cron = cron;
        this.timezone = timezone;
        this.nextRunAt = nextRunAt;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }
}
