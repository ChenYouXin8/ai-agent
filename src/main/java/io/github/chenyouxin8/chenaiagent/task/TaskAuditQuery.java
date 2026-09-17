package io.github.chenyouxin8.chenaiagent.task;

import java.util.List;

public record TaskAuditQuery(int limit, long from, long to, List<TaskEventType> types, String stepId) {
    public TaskAuditQuery {
        limit = Math.max(1, Math.min(limit, 500));
        from = Math.max(0L, from);
        to = to <= 0L ? Long.MAX_VALUE : to;
        types = types == null ? List.of() : List.copyOf(types);
    }
}
