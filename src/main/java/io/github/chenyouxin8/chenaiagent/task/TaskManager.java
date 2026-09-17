package io.github.chenyouxin8.chenaiagent.task;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class TaskManager {
    private final Map<String, ChenTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<TaskEvent>>> listeners = new ConcurrentHashMap<>();
    private final TaskRepository repository;

    public TaskManager(TaskRepository repository) { this.repository = repository; }

    @PostConstruct
    public void restore() {
        try { repository.findAll().forEach(task -> tasks.put(task.getTaskId(), task)); }
        catch (Exception ignored) { }
    }

    public ChenTask create(String prompt) { return create(prompt, "default", "anonymous", "default", TaskPriority.NORMAL); }
    public ChenTask create(String prompt, String ownerId, String sessionId) { return create(prompt, "default", ownerId, sessionId, TaskPriority.NORMAL); }

    public ChenTask create(String prompt, String tenantId, String ownerId, String sessionId, TaskPriority priority) {
        String id = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ChenTask task = new ChenTask(id, prompt);
        task.setTitle(prompt.length() > 32 ? prompt.substring(0, 32) + "..." : prompt);
        task.setTenantId(normalizeIdentity(tenantId, "default"));
        task.setOwnerId(normalizeIdentity(ownerId, "anonymous"));
        task.setSessionId(normalizeIdentity(sessionId, "default"));
        task.setPriority(priority == null ? TaskPriority.NORMAL : priority);
        tasks.put(id, task);
        save(task);
        publish(new TaskEvent(id, TaskEventType.TASK_CREATED, null, "任务已创建"));
        return task;
    }

    public ChenTask get(String taskId) {
        ChenTask task = tasks.get(taskId);
        if (task == null) throw new NoSuchElementException("任务不存在: " + taskId);
        TaskTenantContext.set(task.getTenantId());
        return task;
    }

    public List<ChenTask> list() { return list(null, null, null); }
    public List<ChenTask> list(String ownerId, String sessionId) { return list(null, ownerId, sessionId); }

    public List<ChenTask> list(String tenantId, String ownerId, String sessionId) {
        String normalizedTenant = normalizeIdentity(tenantId, null);
        String normalizedOwner = normalizeIdentity(ownerId, null);
        String normalizedSession = normalizeIdentity(sessionId, null);
        return tasks.values().stream()
                .filter(task -> normalizedTenant == null || normalizedTenant.equals(task.getTenantId()))
                .filter(task -> normalizedOwner == null || normalizedOwner.equals(task.getOwnerId()))
                .filter(task -> normalizedSession == null || normalizedSession.equals(task.getSessionId()))
                .sorted(Comparator.comparing(ChenTask::getCreatedAt).reversed()).toList();
    }

    public List<TaskEvent> history(String taskId, int limit) { return repository.findEvents(taskId, limit); }

    public void save(ChenTask task) {
        TaskTenantContext.set(task.getTenantId());
        task.touch();
        repository.save(task);
    }

    public void publish(TaskEvent event) {
        try { repository.appendEvent(event); }
        catch (RuntimeException ignored) { }
        publishToListeners(event);
    }

    public void publishRequired(TaskEvent event) {
        repository.appendEvent(event);
        publishToListeners(event);
    }

    private void publishToListeners(TaskEvent event) {
        List<Consumer<TaskEvent>> taskListeners = listeners.get(event.getTaskId());
        if (taskListeners != null) taskListeners.forEach(listener -> listener.accept(event));
    }

    public void subscribe(String taskId, Consumer<TaskEvent> listener) {
        listeners.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(String taskId, Consumer<TaskEvent> listener) {
        List<Consumer<TaskEvent>> taskListeners = listeners.get(taskId);
        if (taskListeners != null) taskListeners.remove(listener);
    }

    public void updateStatus(ChenTask task, TaskStatus status, String message) {
        TaskTenantContext.set(task.getTenantId());
        task.setStatus(status);
        save(task);
        publish(new TaskEvent(task.getTaskId(), eventType(status), null, message));
    }

    private TaskEventType eventType(TaskStatus status) {
        return switch (status) {
            case QUEUED -> TaskEventType.TASK_QUEUED;
            case PAUSED -> TaskEventType.TASK_PAUSED;
            case RUNNING -> TaskEventType.TASK_RESUMED;
            case CANCELLED -> TaskEventType.TASK_CANCELLED;
            case COMPLETED -> TaskEventType.TASK_COMPLETED;
            case FAILED -> TaskEventType.TASK_FAILED;
            default -> TaskEventType.MESSAGE;
        };
    }

    private String normalizeIdentity(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().substring(0, Math.min(128, value.trim().length()));
    }
}
