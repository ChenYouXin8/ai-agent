package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskApprovalServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ChenTask waitingTask() {
        ChenTask task = new ChenTask("task_approval", "发布上线");
        task.setStatus(TaskStatus.WAITING_USER);
        task.getSteps().add(new TaskStep("task_approval_step_1", 1, "发布",
                "发布到生产环境", false, List.of(), ApprovalStatus.PENDING));
        return task;
    }

    @Test
    void approveMarksStepApprovedAndQueuesTaskWithoutTransaction() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        service.approve("task_approval", "确认发布", "admin-1");

        TaskStep step = task.getSteps().get(0);
        assertEquals(ApprovalStatus.APPROVED, step.getApprovalStatus());
        assertEquals("确认发布", step.getApprovalNote());
        assertEquals(TaskStatus.QUEUED, task.getStatus());
        verify(manager).save(task, TaskStatus.WAITING_USER);

        ArgumentCaptor<TaskEvent> events = ArgumentCaptor.forClass(TaskEvent.class);
        verify(manager, times(2)).publishRequired(events.capture());
        List<TaskEvent> published = events.getAllValues();
        assertEquals(TaskEventType.TASK_APPROVAL_GRANTED, published.get(0).getType());
        assertEquals("task_approval_step_1", published.get(0).getStepId());
        assertTrue(published.get(0).getMessage().contains("actor=admin-1"));
        assertEquals(TaskEventType.TASK_QUEUED, published.get(1).getType());

        verify(queue).enqueue("task_approval", TaskPriority.NORMAL);
    }

    @Test
    void approveRejectsTaskWithoutPendingApprovalStep() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        task.setStatus(TaskStatus.WAITING_USER);
        task.getSteps().get(0).setApprovalStatus(ApprovalStatus.NONE);
        when(manager.get("task_approval")).thenReturn(task);

        assertThrows(IllegalStateException.class,
                () -> new TaskApprovalService(manager, queue).approve("task_approval", "", "admin-1"));
        verify(manager, never()).save(any(), any());
        verify(queue, never()).enqueue(any(), any());
    }

    @Test
    void approveRejectsTaskNotWaitingUser() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        task.setStatus(TaskStatus.RUNNING);
        when(manager.get("task_approval")).thenReturn(task);

        assertThrows(IllegalStateException.class,
                () -> new TaskApprovalService(manager, queue).approve("task_approval", "", "admin-1"));
        verify(manager, never()).save(any(), any());
        verify(queue, never()).enqueue(any(), any());
    }

    @Test
    void rejectCancelsTaskWithoutEnqueue() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        service.reject("task_approval", null, null);

        TaskStep step = task.getSteps().get(0);
        assertEquals(ApprovalStatus.REJECTED, step.getApprovalStatus());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertTrue(task.getError().contains("人工审批驳回"));

        ArgumentCaptor<TaskEvent> events = ArgumentCaptor.forClass(TaskEvent.class);
        verify(manager, times(2)).publishRequired(events.capture());
        assertTrue(events.getAllValues().get(0).getMessage().contains("actor=anonymous"));
        verify(manager).save(task, TaskStatus.WAITING_USER);
        verify(queue, never()).enqueue(any(), any());
    }

    @Test
    void approveEnqueuesOnlyAfterTransactionCommit() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.approve("task_approval", "确认发布", "admin-1");
            verify(queue, never()).enqueue(any(), any());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            verify(queue).enqueue("task_approval", TaskPriority.NORMAL);
            verify(manager, never()).reload(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void approveRollbackSkipsEnqueueAndReloadsTask() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.approve("task_approval", "确认发布", "admin-1");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(queue, never()).enqueue(any(), any());
            verify(manager).reload("task_approval");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectRollbackReloadsTask() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.reject("task_approval", "信息不足", "admin-1");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(manager).reload("task_approval");
            verify(queue, never()).enqueue(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectCommitDoesNotEnqueueOrReload() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.reject("task_approval", "信息不足", "admin-1");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            verify(queue, never()).enqueue(any(), any());
            verify(manager, never()).reload(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void approveReloadsMemoryWhenGuardedSaveDetectsConcurrentModification() {
        TaskManager manager = mock(TaskManager.class);
        TaskQueueService queue = mock(TaskQueueService.class);
        ChenTask task = waitingTask();
        when(manager.get("task_approval")).thenReturn(task);
        doThrow(new IllegalStateException("任务状态已被并发修改，本次操作未生效：task_approval"))
                .when(manager).save(any(ChenTask.class), any(TaskStatus.class));
        TaskApprovalService service = new TaskApprovalService(manager, queue);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class,
                    () -> service.approve("task_approval", "确认发布", "admin-1"));
            verify(manager, never()).publishRequired(any());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(manager).reload("task_approval");
            verify(queue, never()).enqueue(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
