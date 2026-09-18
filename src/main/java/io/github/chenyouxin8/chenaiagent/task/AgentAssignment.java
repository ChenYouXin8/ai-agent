package io.github.chenyouxin8.chenaiagent.task;

public record AgentAssignment(
        AgentRole role,
        String instruction,
        String handoffPolicy
) {
}
