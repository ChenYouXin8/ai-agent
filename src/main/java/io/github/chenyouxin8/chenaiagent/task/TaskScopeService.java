package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskScopeService {

    public void assertAccess(ChenTask task, String tenantId, String ownerId) {
        String expectedTenant = normalize(tenantId, "default");
        String expectedOwner = normalize(ownerId, "anonymous");
        if (!expectedTenant.equals(task.getTenantId()) || !expectedOwner.equals(task.getOwnerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    public String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(128, normalized.length()));
    }
}
