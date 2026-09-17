package io.github.chenyouxin8.chenaiagent.planner;

public record PlanStep(
        String title,
        String description,
        String type,
        String expectedOutput,
        boolean parallelizable
) {
}
