package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskAuditService {
    private final TaskRepository repository;
    private final TaskManager taskManager;

    public TaskAuditService(TaskRepository repository, TaskManager taskManager) {
        this.repository = repository;
        this.taskManager = taskManager;
    }

    public List<TaskEvent> history(String taskId, TaskAuditQuery query) {
        return repository.findEvents(taskId, query);
    }

    @Transactional
    public void approve(ChenTask task, TaskStep step, String note, String actorId) {
        step.setApprovalStatus(ApprovalStatus.APPROVED);
        step.setApprovalNote(normalizeNote(note));
        task.setStatus(TaskStatus.QUEUED);
        repository.save(task);
        appendAndNotify(new TaskEvent(task.getTaskId(), TaskEventType.TASK_APPROVAL_GRANTED, step.getStepId(),
                approvalMessage("审批通过：", step, actorId)));
        appendAndNotify(new TaskEvent(task.getTaskId(), TaskEventType.TASK_QUEUED, null,
                "人工审批通过，任务重新进入执行队列"));
    }

    @Transactional
    public void reject(ChenTask task, TaskStep step, String note, String actorId) {
        step.setApprovalStatus(ApprovalStatus.REJECTED);
        step.setApprovalNote(normalizeNote(note));
        task.setError("人工审批驳回：" + step.getTitle()
                + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote()));
        task.setStatus(TaskStatus.CANCELLED);
        repository.save(task);
        appendAndNotify(new TaskEvent(task.getTaskId(), TaskEventType.TASK_APPROVAL_REJECTED, step.getStepId(),
                approvalMessage("审批驳回：", step, actorId)));
        appendAndNotify(new TaskEvent(task.getTaskId(), TaskEventType.TASK_CANCELLED, null,
                "任务因人工审批驳回而结束"));
    }

    private void appendAndNotify(TaskEvent event) {
        repository.appendEvent(event);
        taskManager.publishCommitted(event);
    }

    private String approvalMessage(String prefix, TaskStep step, String actorId) {
        String actor = actorId == null || actorId.isBlank() ? "anonymous" : actorId.trim();
        return "actor=" + actor + "；" + prefix + step.getTitle()
                + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote());
    }

    private String normalizeNote(String note) {
        return note == null ? "" : note.trim();
    }
}
