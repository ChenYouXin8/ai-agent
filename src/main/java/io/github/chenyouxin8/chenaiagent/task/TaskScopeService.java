package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskScopeService {

    public void assertAccess(ChenTask task, String tenantId, String ownerId) {
        String expectedTenant = normalize(tenantId, "default");
        String expectedOwner = normalize(ownerId, "anonymous");

        boolean platformAdmin = hasRole("ROLE_PLATFORM_ADMIN");
        boolean tenantAdmin = hasRole("ROLE_TENANT_ADMIN");

        if (!expectedTenant.equals(task.getTenantId()) && !platformAdmin) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }

        if (!expectedOwner.equals(task.getOwnerId()) && !(tenantAdmin || platformAdmin)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    public String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(128, normalized.length()));
    }

    public boolean hasAdminAccess() {
        return hasRole("ROLE_TENANT_ADMIN") || hasRole("ROLE_PLATFORM_ADMIN");
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equalsIgnoreCase(authority.getAuthority()));
    }
}
