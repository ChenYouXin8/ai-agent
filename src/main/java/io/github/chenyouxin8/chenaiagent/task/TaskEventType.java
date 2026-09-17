package io.github.chenyouxin8.chenaiagent.task;

public enum TaskEventType {
    TASK_CREATED,
    PLAN_CREATED,
    STEP_STARTED,
    STEP_COMPLETED,
    STEP_FAILED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    MESSAGE,
    ARTIFACT_CREATED,
    TASK_PAUSED,
    TASK_RESUMED,
    TASK_CANCELLED,
    TASK_COMPLETED,
    TASK_FAILED
}
