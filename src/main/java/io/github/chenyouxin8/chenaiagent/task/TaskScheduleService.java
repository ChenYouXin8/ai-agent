package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Service
public class TaskScheduleService {
    private final TaskAutomationRepository repository;
    private final TaskTemplateService templateService;
    private final TaskManager taskManager;
    private final TaskRuntimeService runtime;
    private final RedisDistributedLockService lockService;

    @Value("${chenmanus.automation.max-catch-up:3}")
    private int maxCatchUp;

    public TaskScheduleService(TaskAutomationRepository repository,
                                TaskTemplateService templateService,
                                TaskManager taskManager,
                                TaskRuntimeService runtime,
                                RedisDistributedLockService lockService) {
        this.repository = repository;
        this.templateService = templateService;
        this.taskManager = taskManager;
        this.runtime = runtime;
        this.lockService = lockService;
    }

    public TaskSchedule create(String templateId, String tenantId, String ownerId, String cron, String timezone) {
        String normalizedTenant = normalize(tenantId, "default");
        String normalizedOwner = normalize(ownerId, "anonymous");
        TaskTemplate template = templateService.get(normalizedTenant, templateId);
        validateCron(cron);
        String zone = validateZone(timezone);
        long next = nextRun(cron, zone, System.currentTimeMillis());
        TaskSchedule schedule = new TaskSchedule(
                "sch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                template.getTemplateId(), normalizedTenant, normalizedOwner, cron.trim(), zone, next);
        repository.saveSchedule(schedule);
        return schedule;
    }

    public List<TaskSchedule> list(String tenantId, String ownerId) {
        return repository.listSchedules(normalize(tenantId, "default"), normalize(ownerId, "anonymous"));
    }

    public TaskSchedule get(String tenantId, String scheduleId) {
        TaskSchedule schedule = repository.findSchedule(normalize(tenantId, "default"), scheduleId);
        if (schedule == null) throw new IllegalArgumentException("定时任务不存在");
        return schedule;
    }

    public void setEnabled(String tenantId, String ownerId, String scheduleId, boolean enabled) {
        TaskSchedule schedule = get(tenantId, scheduleId);
        if (!normalize(ownerId, "anonymous").equals(schedule.getOwnerId())) throw new IllegalArgumentException("无权操作该定时任务");
        schedule.setEnabled(enabled);
        if (enabled && schedule.getNextRunAt() <= System.currentTimeMillis()) {
            schedule.setNextRunAt(nextRun(schedule.getCron(), schedule.getTimezone(), System.currentTimeMillis()));
        }
        schedule.touch();
        repository.saveSchedule(schedule);
    }

    public void delete(String tenantId, String ownerId, String scheduleId) {
        repository.deleteSchedule(normalize(tenantId, "default"), normalize(ownerId, "anonymous"), scheduleId);
    }

    @Scheduled(fixedDelayString = "${chenmanus.automation.poll-ms:15000}")
    public void tick() {
        long now = System.currentTimeMillis();
        for (TaskSchedule schedule : repository.findDueSchedules(now)) {
            if (!lockService.tryLock("schedule:" + schedule.getScheduleId(), java.time.Duration.ofSeconds(30))) continue;
            try {
                trigger(schedule, now);
            } finally {
                lockService.unlock("schedule:" + schedule.getScheduleId());
            }
        }
    }

    private void trigger(TaskSchedule schedule, long now) {
        TaskSchedule current = repository.findSchedule(schedule.getTenantId(), schedule.getScheduleId());
        if (current == null || !current.isEnabled() || current.getNextRunAt() > now) return;
        TaskTemplate template = templateService.get(current.getTenantId(), current.getTemplateId());
        if (!template.isEnabled()) {
            current.setNextRunAt(nextRun(current.getCron(), current.getTimezone(), now));
            current.touch();
            repository.saveSchedule(current);
            return;
        }

        int runs = 0;
        long cursor = current.getNextRunAt();
        while (cursor <= now && runs < Math.max(1, maxCatchUp)) {
            ChenTask task = taskManager.create(
                    templateService.render(template, Map.of(
                            "scheduledAt", Instant.ofEpochMilli(cursor).toString(),
                            "scheduleId", current.getScheduleId(),
                            "templateId", template.getTemplateId()
                    )), current.getTenantId(), current.getOwnerId(),
                    "schedule:" + current.getScheduleId(), template.getPriority());
            runtime.start(task.getTaskId());
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.MESSAGE, null,
                    "由定时任务 " + current.getScheduleId() + " 触发"));
            current.setLastRunAt(cursor);
            cursor = nextRun(current.getCron(), current.getTimezone(), cursor);
            runs++;
        }
        current.setNextRunAt(cursor);
        current.touch();
        repository.saveSchedule(current);
    }

    private long nextRun(String cron, String timezone, long baseMillis) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime base = Instant.ofEpochMilli(baseMillis).atZone(ZoneId.of(timezone));
        ZonedDateTime next = expression.next(base.truncatedTo(ChronoUnit.SECONDS));
        if (next == null) throw new IllegalArgumentException("Cron 没有可计算的下一次触发时间");
        return next.toInstant().toEpochMilli();
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) throw new IllegalArgumentException("Cron 不能为空");
        CronExpression.parse(cron.trim());
    }

    private String validateZone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        try {
            ZoneId.of(value);
            return value;
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("无效时区：" + value);
        }
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String v = value.trim();
        return v.substring(0, Math.min(128, v.length()));
    }
}
