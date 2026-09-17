package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskAuditService {
    private final TaskRepository repository;

    public TaskAuditService(TaskRepository repository) {
        this.repository = repository;
    }

    public List<TaskEvent> history(String taskId, TaskAuditQuery query) {
        return repository.findEvents(taskId, query);
    }

    @Transactional
    public void recordApproval(ChenTask task, TaskStep step, ApprovalStatus status, String note, String actorId) {
        step.setApprovalStatus(status);
        step.setApprovalNote(note == null ? "" : note.trim());
        repository.save(task);
        String actor = actorId == null || actorId.isBlank() ? "anonymous" : actorId.trim();
        TaskEventType approvalType = status == ApprovalStatus.APPROVED
                ? TaskEventType.TASK_APPROVAL_GRANTED : TaskEventType.TASK_APPROVAL_REJECTED;
        repository.appendEvent(new TaskEvent(task.getTaskId(), approvalType, step.getStepId(),
                "actor=" + actor + "；" + (status == ApprovalStatus.APPROVED ? "审批通过：" : "审批驳回：") + step.getTitle()
                        + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote())));
    }
}
