package io.github.chenyouxin8.chenaiagent.planner;

import java.util.List;

public record PlanStep(
        String title,
        String description,
        String type,
        String expectedOutput,
        List<Integer> dependsOn,
        boolean parallelizable
) {
}
