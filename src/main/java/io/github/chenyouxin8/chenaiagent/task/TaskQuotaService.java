package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskQuotaService {

    private final TaskRepository repository;
    private final int maxActiveTasksPerTenant;

    public TaskQuotaService(
            TaskRepository repository,
            @Value("${chenmanus.quota.max-active-tasks-per-tenant:20}") int maxActiveTasksPerTenant
    ) {
        this.repository = repository;
        this.maxActiveTasksPerTenant = Math.max(0, maxActiveTasksPerTenant);
    }

    public void assertCanCreate(String tenantId) {
        if (maxActiveTasksPerTenant <= 0) return;
        int active = repository.countActiveTasks(tenantId);
        if (active >= maxActiveTasksPerTenant) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "租户当前进行中的任务数已达到上限：" + maxActiveTasksPerTenant
            );
        }
    }

    public int activeTasks(String tenantId) {
        return repository.countActiveTasks(tenantId);
    }

    public int maxActiveTasksPerTenant() {
        return maxActiveTasksPerTenant;
    }
}
