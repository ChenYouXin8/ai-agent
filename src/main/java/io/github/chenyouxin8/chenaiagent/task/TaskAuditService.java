package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskAuditService {
    private final TaskRepository repository;

    public TaskAuditService(TaskRepository repository) { this.repository = repository; }

    public List<TaskEvent> history(String taskId, TaskAuditQuery query) {
        return repository.findEvents(taskId, 500).stream()
                .filter(event -> event.getTimestamp() >= query.from() && event.getTimestamp() <= query.to())
                .filter(event -> query.types().isEmpty() || query.types().contains(event.getType()))
                .filter(event -> query.stepId() == null || query.stepId().isBlank() || query.stepId().equals(event.getStepId()))
                .limit(query.limit())
                .toList();
    }
}
