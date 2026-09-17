package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

    @Test
    void savesOfSameTaskAreSerializedWithinJvm() throws Exception {
        TaskRepository repository = mock(TaskRepository.class);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch startBarrier = new CountDownLatch(2);
        doAnswer(invocation -> {
            inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.get(), Math::max);
            Thread.sleep(5);
            inFlight.decrementAndGet();
            return null;
        }).when(repository).save(any(ChenTask.class));

        TaskManager manager = new TaskManager(repository);
        ChenTask task = new ChenTask("task_save_race", "并发保存");
        Runnable saver = () -> {
            startBarrier.countDown();
            try { startBarrier.await(); } catch (InterruptedException e) { return; }
            for (int i = 0; i < 25; i++) manager.save(task);
        };
        Thread first = new Thread(saver, "save-race-1");
        Thread second = new Thread(saver, "save-race-2");
        first.start();
        second.start();
        first.join();
        second.join();

        verify(repository, times(50)).save(any(ChenTask.class));
        assertEquals(1, maxInFlight.get());
    }
}
