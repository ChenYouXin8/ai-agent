package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskApprovalService {
    private final TaskManager taskManager;
    private final TaskQueueService queueService;

    public TaskApprovalService(TaskManager taskManager, TaskQueueService queueService) {
        this.taskManager = taskManager;
        this.queueService = queueService;
    }

    @Transactional
    public void approve(String taskId, String note, String actorId) {
        ChenTask task = taskManager.get(taskId);
        TaskStep step = pendingApprovalStep(task);
        if (step == null || task.getStatus() != TaskStatus.WAITING_USER) {
            throw new IllegalStateException("当前任务没有待审批步骤");
        }
        step.setApprovalStatus(ApprovalStatus.APPROVED);
        step.setApprovalNote(normalize(note));
        task.setStatus(TaskStatus.QUEUED);
        taskManager.save(task);
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_APPROVAL_GRANTED, step.getStepId(),
                message("审批通过：", step, actorId)));
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_QUEUED, null,
                "人工审批通过，任务重新进入执行队列"));
        queueService.enqueue(taskId, task.getPriority());
    }

    @Transactional
    public void reject(String taskId, String note, String actorId) {
        ChenTask task = taskManager.get(taskId);
        TaskStep step = pendingApprovalStep(task);
        if (step == null || task.getStatus() != TaskStatus.WAITING_USER) {
            throw new IllegalStateException("当前任务没有待审批步骤");
        }
        step.setApprovalStatus(ApprovalStatus.REJECTED);
        step.setApprovalNote(normalize(note));
        task.setError("人工审批驳回：" + step.getTitle()
                + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote()));
        task.setStatus(TaskStatus.CANCELLED);
        taskManager.save(task);
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_APPROVAL_REJECTED, step.getStepId(),
                message("审批驳回：", step, actorId)));
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_CANCELLED, null,
                "任务因人工审批驳回而结束"));
    }

    private TaskStep pendingApprovalStep(ChenTask task) {
        return task.getSteps().stream()
                .filter(step -> step.getApprovalStatus() == ApprovalStatus.PENDING)
                .findFirst().orElse(null);
    }

    private String message(String prefix, TaskStep step, String actorId) {
        String actor = actorId == null || actorId.isBlank() ? "anonymous" : actorId.trim();
        return "actor=" + actor + "；" + prefix + step.getTitle()
                + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote());
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
}
