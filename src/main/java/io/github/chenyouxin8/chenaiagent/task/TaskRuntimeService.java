package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.agent.ChenManus;
import io.github.chenyouxin8.chenaiagent.planner.LlmPlanner;
import io.github.chenyouxin8.chenaiagent.planner.Plan;
import io.github.chenyouxin8.chenaiagent.planner.PlanStep;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class TaskRuntimeService {

    private static final int MAX_RETRIES = 2;

    private final TaskManager taskManager;
    private final ToolCallback[] tools;
    private final ChatModel chatModel;
    private final LlmPlanner planner;

    public TaskRuntimeService(
            TaskManager taskManager,
            ToolCallback[] tools,
            ChatModel chatModel,
            LlmPlanner planner
    ) {
        this.taskManager = taskManager;
        this.tools = tools;
        this.chatModel = chatModel;
        this.planner = planner;
    }

    public void start(String taskId) {
        CompletableFuture.runAsync(() -> execute(taskManager.get(taskId)));
    }

    public void pause(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PLANNING) {
            taskManager.updateStatus(task, TaskStatus.PAUSED, "任务已暂停，将在当前执行阶段结束后保持暂停状态");
        }
    }

    public void resume(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.PAUSED) {
            taskManager.updateStatus(task, TaskStatus.RUNNING, "任务已恢复");
            start(taskId);
        }
    }

    public void cancel(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() != TaskStatus.COMPLETED
                && task.getStatus() != TaskStatus.FAILED
                && task.getStatus() != TaskStatus.CANCELLED) {
            taskManager.updateStatus(task, TaskStatus.CANCELLED, "任务已取消");
        }
    }

    private void execute(ChenTask task) {
        if (isStopped(task)) return;

        try {
            if (task.getSteps().isEmpty()) {
                taskManager.updateStatus(task, TaskStatus.PLANNING, "ChenManus 正在生成执行计划");
                createPlan(task);
                taskManager.publish(new TaskEvent(
                        task.getTaskId(), TaskEventType.PLAN_CREATED, null,
                        "已生成 " + task.getSteps().size() + " 个执行步骤"
                ));
            }

            for (TaskStep step : task.getSteps()) {
                if (isStopped(task)) return;
                if (step.getStatus() == StepStatus.COMPLETED || step.getStatus() == StepStatus.SKIPPED) continue;

                taskManager.updateStatus(task, TaskStatus.RUNNING, "执行：" + step.getTitle());
                boolean success = runStepWithRetry(task, step);
                if (!success) {
                    task.setError(step.getError());
                    taskManager.updateStatus(task, TaskStatus.FAILED, "步骤失败：" + step.getTitle());
                    return;
                }
            }

            if (isStopped(task)) return;

            taskManager.updateStatus(task, TaskStatus.REVIEWING, "正在整理并检查执行结果");
            task.setResult(task.getSteps().stream()
                    .map(TaskStep::getOutput)
                    .filter(output -> output != null && !output.isBlank())
                    .collect(Collectors.joining("\n\n")));
            taskManager.updateStatus(task, TaskStatus.COMPLETED, "任务完成");
        } catch (Exception e) {
            task.setError(e.getMessage());
            taskManager.updateStatus(task, TaskStatus.FAILED, "任务失败：" + e.getMessage());
        }
    }

    private void createPlan(ChenTask task) {
        Plan plan = planner.createPlan(task.getPrompt());
        task.setTitle(plan.title());
        task.setPlanSummary(plan.summary());
        for (PlanStep planStep : plan.steps()) {
            int sequence = task.getSteps().size() + 1;
            String description = safe(planStep.description(), "执行该计划步骤");
            String expected = safe(planStep.expectedOutput(), "完成该步骤并返回可验证的结果");
            String type = safe(planStep.type(), "GENERAL");
            String enrichedDescription = "类型：" + type + "\n" + description + "\n预期输出：" + expected;
            task.getSteps().add(new TaskStep(
                    task.getTaskId() + "_step_" + sequence,
                    sequence,
                    planStep.title().trim(),
                    enrichedDescription
            ));
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.STEP_PLANNED,
                    task.getTaskId() + "_step_" + sequence,
                    planStep.title().trim()
            ));
        }
        task.touch();
    }

    private boolean runStepWithRetry(ChenTask task, TaskStep step) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    step.setRetryCount(attempt);
                    step.setStatus(StepStatus.PENDING);
                    step.setError(null);
                    taskManager.publish(new TaskEvent(
                            task.getTaskId(), TaskEventType.STEP_RETRY,
                            step.getStepId(),
                            "第 " + attempt + " 次重试：" + step.getTitle()
                    ));
                }
                runStep(task, step);
                return true;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) return false;
            }
        }
        return false;
    }

    private void runStep(ChenTask task, TaskStep step) {
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(System.currentTimeMillis());
        taskManager.publish(new TaskEvent(
                task.getTaskId(), TaskEventType.STEP_STARTED,
                step.getStepId(), step.getTitle()
        ));

        try {
            ChenManus agent = new ChenManus(tools, chatModel);
            String output = agent.run(buildStepPrompt(task, step));
            if (output == null || output.isBlank()) {
                throw new IllegalStateException("Agent 未返回有效结果");
            }
            step.setOutput(output);
            step.setStatus(StepStatus.COMPLETED);
            step.setError(null);
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.STEP_COMPLETED,
                    step.getStepId(), output
            ));
        } catch (Exception e) {
            step.setError(e.getMessage());
            step.setStatus(StepStatus.FAILED);
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.STEP_FAILED,
                    step.getStepId(), e.getMessage()
            ));
            throw e;
        } finally {
            step.setCompletedAt(System.currentTimeMillis());
            task.touch();
        }
    }

    private String buildStepPrompt(ChenTask task, TaskStep step) {
        String previousOutputs = task.getSteps().stream()
                .filter(candidate -> candidate.getSequence() < step.getSequence())
                .filter(candidate -> candidate.getOutput() != null && !candidate.getOutput().isBlank())
                .map(candidate -> "步骤 " + candidate.getSequence() + " - " + candidate.getTitle() + ":\n" + truncate(candidate.getOutput(), 3500))
                .collect(Collectors.joining("\n\n"));

        return """
                你是 ChenManus 2.0 的任务执行 Agent。
                当前总任务：%s
                当前执行步骤：%s
                步骤说明：%s

                规则：
                1. 只关注当前步骤，但要利用已有步骤结果。
                2. 能使用工具时优先使用工具完成实际工作，而不是只给建议。
                3. 不要伪造工具执行结果；无法完成时明确说明原因。
                4. 当前步骤完成后，返回清晰、可验证的结果。

                已完成步骤结果：
                %s
                """.formatted(
                task.getPrompt(),
                step.getTitle(),
                step.getDescription(),
                previousOutputs.isBlank() ? "（无）" : previousOutputs
        );
    }

    private boolean isStopped(ChenTask task) {
        return task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.PAUSED;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
