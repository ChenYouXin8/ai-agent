package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class TaskManager {
    private final Map<String, ChenTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<TaskEvent>>> listeners = new ConcurrentHashMap<>();

    public ChenTask create(String prompt) {
        String id = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ChenTask task = new ChenTask(id, prompt);
        task.setTitle(prompt.length() > 32 ? prompt.substring(0, 32) + "..." : prompt);
        tasks.put(id, task);
        publish(new TaskEvent(id, TaskEventType.TASK_CREATED, null, "任务已创建"));
        return task;
    }

    public ChenTask get(String taskId) {
        ChenTask task = tasks.get(taskId);
        if (task == null) throw new NoSuchElementException("任务不存在: " + taskId);
        return task;
    }

    public List<ChenTask> list() {
        return tasks.values().stream().sorted(Comparator.comparing(ChenTask::getCreatedAt).reversed()).toList();
    }

    public void publish(TaskEvent event) {
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
        task.setStatus(status);
        task.touch();
        publish(new TaskEvent(task.getTaskId(), eventType(status), null, message));
    }

    private TaskEventType eventType(TaskStatus status) {
        return switch (status) {
            case PAUSED -> TaskEventType.TASK_PAUSED;
            case RUNNING -> TaskEventType.TASK_RESUMED;
            case CANCELLED -> TaskEventType.TASK_CANCELLED;
            case COMPLETED -> TaskEventType.TASK_COMPLETED;
            case FAILED -> TaskEventType.TASK_FAILED;
            default -> TaskEventType.MESSAGE;
        };
    }
}
