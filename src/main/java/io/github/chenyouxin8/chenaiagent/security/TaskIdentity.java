package io.github.chenyouxin8.chenaiagent.security;

import java.util.Set;

public record TaskIdentity(String tenantId, String userId, Set<String> roles, boolean authenticated) {
    public boolean hasRole(String role) {
        return roles != null && roles.stream().anyMatch(value -> value.equalsIgnoreCase(role));
    }

    public boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
    }

    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }
}
