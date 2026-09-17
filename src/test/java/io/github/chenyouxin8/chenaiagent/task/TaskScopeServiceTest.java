package io.github.chenyouxin8.chenaiagent.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskScopeServiceTest {

    private final TaskScopeService scopeService = new TaskScopeService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void normalUserCanOnlyAccessOwnTask() {
        ChenTask task = task("tenant-a", "user-a");

        assertDoesNotThrow(() -> scopeService.assertAccess(task, "tenant-a", "user-a"));
        assertThrows(ResponseStatusException.class,
                () -> scopeService.assertAccess(task, "tenant-a", "user-b"));
        assertThrows(ResponseStatusException.class,
                () -> scopeService.assertAccess(task, "tenant-b", "user-a"));
    }

    @Test
    void tenantAdminCanAccessOtherUsersInSameTenant() {
        authenticate("ROLE_TENANT_ADMIN");
        ChenTask task = task("tenant-a", "user-a");

        assertDoesNotThrow(() -> scopeService.assertAccess(task, "tenant-a", "user-b"));
        assertThrows(ResponseStatusException.class,
                () -> scopeService.assertAccess(task, "tenant-b", "user-b"));
    }

    @Test
    void platformAdminCanAccessAcrossTenants() {
        authenticate("ROLE_PLATFORM_ADMIN");
        ChenTask task = task("tenant-a", "user-a");

        assertDoesNotThrow(() -> scopeService.assertAccess(task, "tenant-b", "user-b"));
    }

    private void authenticate(String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin",
                        "N/A",
                        List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ChenTask task(String tenantId, String ownerId) {
        ChenTask task = new ChenTask("task_scope_test", "scope");
        task.setTenantId(tenantId);
        task.setOwnerId(ownerId);
        return task;
    }
}
