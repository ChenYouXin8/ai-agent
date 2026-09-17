package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentHandoffService {

    private static final int MAX_OUTPUT_LENGTH = 3000;

    public String buildHandoff(ChenTask task, TaskStep step, AgentAssignment assignment) {
        List<Integer> dependencies = step.getDependsOn() == null ? List.of() : step.getDependsOn();
        String dependencySummary = task.getSteps().stream()
                .filter(candidate -> dependencies.contains(candidate.getSequence()))
                .filter(candidate -> candidate.getOutput() != null && !candidate.getOutput().isBlank())
                .map(candidate -> "Step " + candidate.getSequence() + " / " + candidate.getTitle()
                        + "：\n" + truncate(candidate.getOutput()))
                .collect(Collectors.joining("\n\n"));
        return "角色：" + assignment.role().name()
                + "\n当前步骤：" + step.getTitle()
                + "\n交接规则：" + assignment.handoffPolicy()
                + "\n依赖结果：\n" + (dependencySummary.isBlank() ? "（无）" : dependencySummary);
    }

    public void publish(ChenTask task, TaskStep step, AgentAssignment assignment, TaskManager taskManager) {
        taskManager.publish(new TaskEvent(
                task.getTaskId(),
                TaskEventType.AGENT_HANDOFF,
                step.getStepId(),
                "Team → " + assignment.role().name() + "：接收依赖上下文"
        ));
    }

    private String truncate(String value) {
        if (value.length() <= MAX_OUTPUT_LENGTH) return value;
        return value.substring(0, MAX_OUTPUT_LENGTH) + "...";
    }
}
