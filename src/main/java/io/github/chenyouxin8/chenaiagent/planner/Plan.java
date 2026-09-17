package io.github.chenyouxin8.chenaiagent.planner;

import java.util.List;

public record Plan(
        String title,
        String summary,
        List<PlanStep> steps
) {
}
