package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskQuotaServiceTest {

    @Test
    void shouldRejectTenantWhenActiveTaskQuotaIsReached() {
        TaskRepository repository = mock(TaskRepository.class);
        when(repository.countActiveTasks("tenant-a")).thenReturn(2);

        TaskQuotaService quota = new TaskQuotaService(repository, 2);

        assertThrows(RuntimeException.class, () -> quota.assertCanCreate("tenant-a"));
        assertEquals(2, quota.activeTasks("tenant-a"));
        assertEquals(2, quota.maxActiveTasksPerTenant());
    }

    @Test
    void zeroQuotaMeansUnlimited() {
        TaskRepository repository = mock(TaskRepository.class);
        when(repository.countActiveTasks("tenant-a")).thenReturn(999);

        TaskQuotaService quota = new TaskQuotaService(repository, 0);

        assertDoesNotThrow(() -> quota.assertCanCreate("tenant-a"));
    }
}
