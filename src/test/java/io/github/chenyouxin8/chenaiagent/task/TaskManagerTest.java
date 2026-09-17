package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskManagerTest {

    @Test
    void publishPersistsEventAndNotifiesSubscribers() {
        TaskRepository repository = mock(TaskRepository.class);
        TaskManager manager = new TaskManager(repository);
        List<TaskEvent> received = new ArrayList<>();
        manager.subscribe("task_publish", received::add);

        TaskEvent event = new TaskEvent("task_publish", TaskEventType.TASK_CREATED, null, "任务已创建");
        manager.publish(event);

        verify(repository).appendEvent(event);
        assertEquals(List.of(event), received);
    }

    @Test
    void publishPropagatesPersistenceFailureAndSkipsSubscribers() {
        TaskRepository repository = mock(TaskRepository.class);
        doThrow(new IllegalStateException("事件持久化失败")).when(repository).appendEvent(any());
        TaskManager manager = new TaskManager(repository);
        List<TaskEvent> received = new ArrayList<>();
        manager.subscribe("task_publish", received::add);

        TaskEvent event = new TaskEvent("task_publish", TaskEventType.TASK_APPROVAL_REQUIRED, "step-1", "需要人工确认");
        assertThrows(IllegalStateException.class, () -> manager.publish(event));

        assertTrue(received.isEmpty());
        verify(repository).appendEvent(event);
    }
}
