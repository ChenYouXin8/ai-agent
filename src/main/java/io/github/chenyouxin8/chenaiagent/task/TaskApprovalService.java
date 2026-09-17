package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        registerApprovalSynchronization(taskId, task.getPriority(), true);
        step.setApprovalStatus(ApprovalStatus.APPROVED);
        step.setApprovalNote(normalize(note));
        task.setStatus(TaskStatus.QUEUED);
        taskManager.save(task, TaskStatus.WAITING_USER);
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_APPROVAL_GRANTED, step.getStepId(),
                message("审批通过：", step, actorId)));
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_QUEUED, null,
                "人工审批通过，任务重新进入执行队列"));
    }

    @Transactional
    public void reject(String taskId, String note, String actorId) {
        ChenTask task = taskManager.get(taskId);
        TaskStep step = pendingApprovalStep(task);
        if (step == null || task.getStatus() != TaskStatus.WAITING_USER) {
            throw new IllegalStateException("当前任务没有待审批步骤");
        }
        registerApprovalSynchronization(taskId, task.getPriority(), false);
        step.setApprovalStatus(ApprovalStatus.REJECTED);
        step.setApprovalNote(normalize(note));
        task.setError("人工审批驳回：" + step.getTitle()
                + (step.getApprovalNote().isBlank() ? "" : "；" + step.getApprovalNote()));
        task.setStatus(TaskStatus.CANCELLED);
        taskManager.save(task, TaskStatus.WAITING_USER);
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_APPROVAL_REJECTED, step.getStepId(),
                message("审批驳回：", step, actorId)));
        taskManager.publishRequired(new TaskEvent(taskId, TaskEventType.TASK_CANCELLED, null,
                "任务因人工审批驳回而结束"));
    }

    // 并发防护分两层：TaskManager.get 返回共享实例，同 JVM 并发请求会被前置状态检查拦截；
    // 跨实例/交错场景由 save(task, WAITING_USER) 的条件更新兜底，后到方抛 IllegalStateException 回滚。
    // 事务同步须在修改内存聚合之前注册：提交后才入队（入队是事务外副作用），回滚/守卫失败后从数据库重载内存状态。
    private void registerApprovalSynchronization(String taskId, TaskPriority priority, boolean enqueueOnCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (enqueueOnCommit) queueService.enqueue(taskId, priority);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    if (enqueueOnCommit) queueService.enqueue(taskId, priority);
                } else {
                    taskManager.reload(taskId);
                }
            }
        });
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
