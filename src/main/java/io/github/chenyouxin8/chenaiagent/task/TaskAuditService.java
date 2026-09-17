package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskAuditService {
    private final TaskRepository repository;

    public TaskAuditService(TaskRepository repository) { this.repository = repository; }

    public List<TaskEvent> history(String taskId, TaskAuditQuery query) {
        return repository.findEvents(taskId, query);
    }
}
