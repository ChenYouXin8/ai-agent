package io.github.chenyouxin8.chenaiagent.task;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
public class TaskManager {
    private static final int SAVE_LOCK_STRIPES = 16;

    private final Map<String, ChenTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<TaskEvent>>> listeners = new ConcurrentHashMap<>();
    private final TaskRepository repository;
    private final ReentrantLock[] saveLocks;

    public TaskManager(TaskRepository repository) {
        this.repository = repository;
        this.saveLocks = new ReentrantLock[SAVE_LOCK_STRIPES];
        for (int i = 0; i < SAVE_LOCK_STRIPES; i++) saveLocks[i] = new ReentrantLock();
    }

    // 启动时 DB 不可用必须让应用启动失败：静默吞掉会让内存缓存为空（已有任务全部 404），
    // 而 quota 等路径直接读库，两套数据源从此分叉
    @PostConstruct
    public void restore() {
        repository.findAll().forEach(task -> tasks.put(task.getTaskId(), task));
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

    // 事务回滚后内存中的聚合已被业务代码修改，用数据库状态覆盖，避免脏状态被后续读消费
    public void reload(String taskId) {
        try {
            ChenTask task = repository.find(taskId);
            if (task != null) tasks.put(taskId, task);
        } catch (RuntimeException ignored) { }
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
        withSaveLock(task.getTaskId(), () -> repository.save(task));
    }

    // 条件保存：数据库中任务仍为 expectedStatus 才写入，用于审批这类不允许并发交错的状态转移
    public void save(ChenTask task, TaskStatus expectedStatus) {
        TaskTenantContext.set(task.getTenantId());
        task.touch();
        withSaveLock(task.getTaskId(), () -> repository.save(task, expectedStatus));
    }

    // 同 JVM 内按任务条带锁串行化保存（旧的全局 synchronized 会串行化所有无关任务）；
    // 跨实例的权威串行化在数据库层：repository.save 的单事务 + 任务行锁。条带锁释放早于外层事务提交时，
    // 后到的同任务保存会在行锁上等待，正确性不受影响
    private void withSaveLock(String taskId, Runnable saveAction) {
        ReentrantLock lock = saveLocks[Math.floorMod(taskId.hashCode(), saveLocks.length)];
        lock.lock();
        try {
            saveAction.run();
        } finally {
            lock.unlock();
        }
    }

    // 事件持久化失败必须让调用方感知（事务内则随之回滚）：吞掉异常会导致 SSE 与审计历史分叉，
    // 且 TASK_APPROVAL_REQUIRED 等关键事件静默丢失后审批环节在审计链上无迹可查
    public void publish(TaskEvent event) {
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
