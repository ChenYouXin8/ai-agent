package io.github.chenyouxin8.chenaiagent.task;

/**
 * Binds the current task tenant to the executing worker thread.
 * This lets lower-level services such as semantic memory inherit tenant scope
 * without widening every existing method signature.
 */
public final class TaskTenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TaskTenantContext() {
    }

    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(tenantId.trim());
        }
    }

    public static String get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
