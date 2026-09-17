package io.github.chenyouxin8.chenaiagent.planner;

import java.util.List;

public record PlanStep(
        String title,
        String description,
        String type,
        String expectedOutput,
        List<Integer> dependsOn,
        boolean parallelizable,
        boolean requiresApproval
) {
    public PlanStep(String title,
                    String description,
                    String type,
                    String expectedOutput,
                    List<Integer> dependsOn,
                    boolean parallelizable) {
        this(title, description, type, expectedOutput, dependsOn, parallelizable, false);
    }

    public PlanStep(String title,
                    String description,
                    String type,
                    String expectedOutput,
                    boolean parallelizable) {
        this(title, description, type, expectedOutput, List.of(), parallelizable, false);
    }
}
