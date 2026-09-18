package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TaskAutomationRepository {
    private final JdbcTemplate jdbc;

    public TaskAutomationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void saveTemplate(TaskTemplate template) {
        int updated = jdbc.update("""
                UPDATE chen_task_templates SET name=?, prompt_template=?, priority=?, approval_required=?, enabled=?, updated_at=?
                WHERE template_id=? AND tenant_id=? AND owner_id=?
                """, template.getName(), template.getPromptTemplate(), template.getPriority().name(),
                template.isApprovalRequired(), template.isEnabled(), template.getUpdatedAt(),
                template.getTemplateId(), template.getTenantId(), template.getOwnerId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO chen_task_templates
                    (template_id, tenant_id, owner_id, name, prompt_template, priority, approval_required, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, template.getTemplateId(), template.getTenantId(), template.getOwnerId(), template.getName(),
                    template.getPromptTemplate(), template.getPriority().name(), template.isApprovalRequired(), template.isEnabled(),
                    template.getCreatedAt(), template.getUpdatedAt());
        }
    }

    public TaskTemplate findTemplate(String tenantId, String templateId) {
        List<TaskTemplate> values = jdbc.query("""
                SELECT template_id, tenant_id, owner_id, name, prompt_template, priority, approval_required, enabled, created_at, updated_at
                FROM chen_task_templates WHERE tenant_id=? AND template_id=?
                """, (rs, n) -> {
            TaskTemplate t = new TaskTemplate(rs.getString("template_id"), rs.getString("name"),
                    rs.getString("prompt_template"), rs.getString("tenant_id"), rs.getString("owner_id"),
                    TaskPriority.from(rs.getString("priority")), rs.getBoolean("approval_required"));
            t.setEnabled(rs.getBoolean("enabled"));
            return t;
        }, tenantId, templateId);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<TaskTemplate> listTemplates(String tenantId, String ownerId) {
        return jdbc.query("""
                SELECT template_id, tenant_id, owner_id, name, prompt_template, priority, approval_required, enabled, created_at, updated_at
                FROM chen_task_templates WHERE tenant_id=? AND (? IS NULL OR owner_id=?) ORDER BY created_at DESC
                """, (rs, n) -> {
            TaskTemplate t = new TaskTemplate(rs.getString("template_id"), rs.getString("name"),
                    rs.getString("prompt_template"), rs.getString("tenant_id"), rs.getString("owner_id"),
                    TaskPriority.from(rs.getString("priority")), rs.getBoolean("approval_required"));
            t.setEnabled(rs.getBoolean("enabled"));
            return t;
        }, tenantId, ownerId, ownerId);
    }

    public synchronized void saveSchedule(TaskSchedule schedule) {
        int updated = jdbc.update("""
                UPDATE chen_task_schedules SET cron=?, timezone=?, enabled=?, next_run_at=?, last_run_at=?, updated_at=?
                WHERE schedule_id=? AND tenant_id=? AND owner_id=?
                """, schedule.getCron(), schedule.getTimezone(), schedule.isEnabled(), schedule.getNextRunAt(),
                schedule.getLastRunAt(), schedule.getUpdatedAt(), schedule.getScheduleId(), schedule.getTenantId(), schedule.getOwnerId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO chen_task_schedules
                    (schedule_id, template_id, tenant_id, owner_id, cron, timezone, enabled, next_run_at, last_run_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, schedule.getScheduleId(), schedule.getTemplateId(), schedule.getTenantId(), schedule.getOwnerId(),
                    schedule.getCron(), schedule.getTimezone(), schedule.isEnabled(), schedule.getNextRunAt(), schedule.getLastRunAt(),
                    schedule.getCreatedAt(), schedule.getUpdatedAt());
        }
    }

    public TaskSchedule findSchedule(String tenantId, String scheduleId) {
        List<TaskSchedule> values = jdbc.query("""
                SELECT schedule_id, template_id, tenant_id, owner_id, cron, timezone, enabled, next_run_at, last_run_at, created_at, updated_at
                FROM chen_task_schedules WHERE tenant_id=? AND schedule_id=?
                """, this::mapSchedule, tenantId, scheduleId);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<TaskSchedule> listSchedules(String tenantId, String ownerId) {
        return jdbc.query("""
                SELECT schedule_id, template_id, tenant_id, owner_id, cron, timezone, enabled, next_run_at, last_run_at, created_at, updated_at
                FROM chen_task_schedules WHERE tenant_id=? AND (? IS NULL OR owner_id=?) ORDER BY created_at DESC
                """, this::mapSchedule, tenantId, ownerId, ownerId);
    }

    public List<TaskSchedule> findDueSchedules(long now) {
        return jdbc.query("""
                SELECT schedule_id, template_id, tenant_id, owner_id, cron, timezone, enabled, next_run_at, last_run_at, created_at, updated_at
                FROM chen_task_schedules WHERE enabled=TRUE AND next_run_at <= ? ORDER BY next_run_at
                """, this::mapSchedule, now);
    }

    public void deleteSchedule(String tenantId, String ownerId, String scheduleId) {
        jdbc.update("DELETE FROM chen_task_schedules WHERE tenant_id=? AND owner_id=? AND schedule_id=?", tenantId, ownerId, scheduleId);
    }

    private TaskSchedule mapSchedule(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        TaskSchedule schedule = new TaskSchedule(rs.getString("schedule_id"), rs.getString("template_id"),
                rs.getString("tenant_id"), rs.getString("owner_id"), rs.getString("cron"), rs.getString("timezone"),
                rs.getLong("next_run_at"));
        schedule.setEnabled(rs.getBoolean("enabled"));
        schedule.setLastRunAt(rs.getLong("last_run_at"));
        return schedule;
    }
}
