package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.agent.ChenManus;
import io.github.chenyouxin8.chenaiagent.planner.LlmPlanner;
import io.github.chenyouxin8.chenaiagent.planner.Plan;
import io.github.chenyouxin8.chenaiagent.planner.PlanStep;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class TaskRuntimeService {

    private static final int MAX_RETRIES = 2;
    private static final int MAX_REPAIR_ROUNDS = 1;

    private final TaskManager taskManager;
    private final ToolCallback[] tools;
    private final ChatModel chatModel;
    private final LlmPlanner planner;
    private final TaskReviewerService reviewer;
    private final TaskMemoryService memoryService;
    private final ArtifactService artifactService;
    private final AgentRolePromptService rolePromptService;

    public TaskRuntimeService(
            TaskManager taskManager,
            ToolCallback[] tools,
            ChatModel chatModel,
            LlmPlanner planner,
            TaskReviewerService reviewer,
            TaskMemoryService memoryService,
            ArtifactService artifactService,
            AgentRolePromptService rolePromptService
    ) {
        this.taskManager = taskManager;
        this.tools = tools;
        this.chatModel = chatModel;
        this.planner = planner;
        this.reviewer = reviewer;
        this.memoryService = memoryService;
        this.artifactService = artifactService;
        this.rolePromptService = rolePromptService;
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
                taskManager.save(task);
                taskManager.publish(new TaskEvent(
                        task.getTaskId(), TaskEventType.PLAN_CREATED, null,
                        "已生成 " + task.getSteps().size() + " 个执行步骤"
                ));
            }

            if (!runPendingSteps(task) || isStopped(task) || task.getStatus() == TaskStatus.FAILED) return;

            taskManager.updateStatus(task, TaskStatus.REVIEWING, "Reviewer 正在验收任务结果");
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_STARTED, null, "开始结果审核"));
            ReviewDecision decision = reviewer.review(task);
            task.setReview(decision);
            taskManager.save(task);
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.REVIEW_COMPLETED, null,
                    decision.feedback() == null ? "审核完成" : decision.feedback()
            ));

            for (int repairRound = 0; !decision.passed() && repairRound < MAX_REPAIR_ROUNDS; repairRound++) {
                TaskStep repairStep = createRepairStep(task, decision);
                if (!runStepWithRetry(task, repairStep)) break;
                taskManager.updateStatus(task, TaskStatus.REVIEWING, "补救步骤完成，Reviewer 正在二次验收");
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_STARTED, null, "开始二次审核"));
                decision = reviewer.review(task);
                task.setReview(decision);
                taskManager.save(task);
                taskManager.publish(new TaskEvent(
                        task.getTaskId(), TaskEventType.REVIEW_COMPLETED, null,
                        decision.feedback() == null ? "二次审核完成" : decision.feedback()
                ));
            }

            task.setResult(task.getSteps().stream()
                    .map(TaskStep::getOutput)
                    .filter(output -> output != null && !output.isBlank())
                    .collect(Collectors.joining("\n\n")));
            taskManager.save(task);

            if (!decision.passed()) {
                task.setError("Reviewer 未通过：" + safe(decision.missingItems(), decision.feedback()));
                taskManager.save(task);
                memoryService.remember(task);
                taskManager.updateStatus(task, TaskStatus.FAILED, task.getError());
                return;
            }

            memoryService.remember(task);
            taskManager.save(task);
            taskManager.updateStatus(task, TaskStatus.COMPLETED, "任务完成并通过审核");
        } catch (Exception e) {
            task.setError(e.getMessage());
            taskManager.save(task);
            memoryService.remember(task);
            taskManager.updateStatus(task, TaskStatus.FAILED, "任务失败：" + e.getMessage());
        }
    }

    private boolean runPendingSteps(ChenTask task) {
        int index = 0;
        while (index < task.getSteps().size()) {
            if (isStopped(task)) return false;

            TaskStep current = task.getSteps().get(index);
            if (current.getStatus() == StepStatus.COMPLETED || current.getStatus() == StepStatus.SKIPPED) {
                index++;
                continue;
            }

            if (current.isParallelizable()) {
                List<TaskStep> parallelSteps = new ArrayList<>();
                int cursor = index;
                while (cursor < task.getSteps().size()) {
                    TaskStep candidate = task.getSteps().get(cursor);
                    if (candidate.getStatus() == StepStatus.COMPLETED || candidate.getStatus() == StepStatus.SKIPPED) {
                        cursor++;
                        continue;
                    }
                    if (!candidate.isParallelizable()) break;
                    parallelSteps.add(candidate);
                    cursor++;
                }

                if (parallelSteps.size() > 1) {
                    taskManager.updateStatus(task, TaskStatus.RUNNING, "并行执行 " + parallelSteps.size() + " 个独立步骤");
                    taskManager.publish(new TaskEvent(
                            task.getTaskId(), TaskEventType.MESSAGE, null,
                            "启动并行步骤：" + parallelSteps.stream().map(TaskStep::getTitle).collect(Collectors.joining("、"))
                    ));

                    List<CompletableFuture<Boolean>> futures = parallelSteps.stream()
                            .map(step -> CompletableFuture.supplyAsync(() -> runStepWithRetry(task, step)))
                            .toList();
                    boolean allSucceeded = futures.stream().allMatch(CompletableFuture::join);
                    taskManager.save(task);
                    if (!allSucceeded) {
                        TaskStep failed = parallelSteps.stream()
                                .filter(step -> step.getStatus() == StepStatus.FAILED)
                                .findFirst()
                                .orElse(parallelSteps.get(0));
                        task.setError(failed.getError());
                        taskManager.updateStatus(task, TaskStatus.FAILED, "并行步骤失败：" + failed.getTitle());
                        return false;
                    }
                    index = cursor;
                    continue;
                }
            }

            taskManager.updateStatus(task, TaskStatus.RUNNING, "执行：" + current.getTitle());
            boolean success = runStepWithRetry(task, current);
            if (!success) {
                task.setError(current.getError());
                taskManager.save(task);
                taskManager.updateStatus(task, TaskStatus.FAILED, "步骤失败：" + current.getTitle());
                return false;
            }
            index++;
        }
        return true;
    }

    private void createPlan(ChenTask task) {
        String memoryContext = memoryService.recallContext(task.getOwnerId(), task.getSessionId(), task.getPrompt(), 3);
        String planningPrompt = task.getPrompt();
        if (!memoryContext.isBlank()) {
            planningPrompt += "\n\n以下是同一用户/会话的历史任务记忆，仅用于参考做法，不可当作当前任务事实：\n" + memoryContext;
        }

        Plan plan = planner.createPlan(planningPrompt);
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
                    enrichedDescription,
                    planStep.parallelizable()
            ));
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.STEP_PLANNED,
                    task.getTaskId() + "_step_" + sequence,
                    planStep.title().trim() + (planStep.parallelizable() ? "（可并行）" : "")
            ));
        }
        task.touch();
    }

    private TaskStep createRepairStep(ChenTask task, ReviewDecision decision) {
        int sequence = task.getSteps().size() + 1;
        String missing = safe(decision.missingItems(), "补足审核发现的缺失内容");
        String feedback = safe(decision.feedback(), "重新检查并修正最终结果");
        TaskStep repairStep = new TaskStep(
                task.getTaskId() + "_step_" + sequence,
                sequence,
                "补救与修正",
                "根据 Reviewer 反馈修正结果。\n缺失项：" + missing + "\n审核反馈：" + feedback,
                false
        );
        task.getSteps().add(repairStep);
        taskManager.save(task);
        taskManager.publish(new TaskEvent(
                task.getTaskId(), TaskEventType.STEP_PLANNED,
                repairStep.getStepId(), repairStep.getTitle()
        ));
        return repairStep;
    }

    private boolean runStepWithRetry(ChenTask task, TaskStep step) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    step.setRetryCount(attempt);
                    step.setStatus(StepStatus.PENDING);
                    step.setError(null);
                    taskManager.save(task);
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
        taskManager.save(task);
        taskManager.publish(new TaskEvent(
                task.getTaskId(), TaskEventType.STEP_STARTED,
                step.getStepId(), step.getTitle()
        ));

        try {
            AgentRole role = rolePromptService.resolve(extractType(step.getDescription()), step.getTitle());
            ChenManus agent = new ChenManus(tools, chatModel);
            agent.setToolObserver((phase, toolName, detail) -> {
                TaskEventType type = switch (phase) {
                    case "started" -> TaskEventType.TOOL_STARTED;
                    case "completed" -> TaskEventType.TOOL_COMPLETED;
                    case "failed" -> TaskEventType.TOOL_FAILED;
                    default -> TaskEventType.MESSAGE;
                };
                taskManager.publish(new TaskEvent(
                        task.getTaskId(), type, step.getStepId(),
                        toolName + (detail == null || detail.isBlank() ? "" : "：" + detail)
                ));
            });
            String output = agent.run(buildStepPrompt(task, step, role));
            if (output == null || output.isBlank()) throw new IllegalStateException("Agent 未返回有效结果");
            step.setOutput(output);
            step.setStatus(StepStatus.COMPLETED);
            step.setError(null);
            artifactService.capture(task, output, taskManager);
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_COMPLETED, step.getStepId(), output));
        } catch (Exception e) {
            step.setError(e.getMessage());
            step.setStatus(StepStatus.FAILED);
            taskManager.save(task);
            taskManager.publish(new TaskEvent(
                    task.getTaskId(), TaskEventType.STEP_FAILED, step.getStepId(), e.getMessage()
            ));
            throw e;
        } finally {
            step.setCompletedAt(System.currentTimeMillis());
            task.touch();
            taskManager.save(task);
        }
    }

    private String buildStepPrompt(ChenTask task, TaskStep step, AgentRole role) {
        String previousOutputs = task.getSteps().stream()
                .filter(candidate -> candidate.getSequence() < step.getSequence())
                .filter(candidate -> candidate.getOutput() != null && !candidate.getOutput().isBlank())
                .map(candidate -> "步骤 " + candidate.getSequence() + " - " + candidate.getTitle() + ":\n" + truncate(candidate.getOutput(), 3500))
                .collect(Collectors.joining("\n\n"));
        String memoryContext = memoryService.recallContext(task.getOwnerId(), task.getSessionId(), task.getPrompt(), 2);
        String memorySection = memoryContext.isBlank() ? "（无）" : memoryContext;

        return """
                你是 ChenManus 2.0 的任务执行 Agent。
                %s
                当前总任务：%s
                当前执行步骤：%s
                步骤说明：%s

                规则：
                1. 只关注当前步骤，但要利用已有步骤结果和历史任务记忆。
                2. 历史记忆只用于参考，不得把其中内容当作当前事实。
                3. 能使用工具时优先使用工具完成实际工作，而不是只给建议。
                4. 不要伪造工具执行结果；无法完成时明确说明原因。
                5. 当前步骤完成后，返回清晰、可验证的结果。

                已完成步骤结果：
                %s

                同一用户/会话历史任务记忆：
                %s
                """.formatted(
                rolePromptService.instruction(role),
                task.getPrompt(),
                step.getTitle(),
                step.getDescription(),
                previousOutputs.isBlank() ? "（无）" : previousOutputs,
                memorySection
        );
    }

    private String extractType(String description) {
        if (description == null) return "GENERAL";
        String marker = "类型：";
        int index = description.indexOf(marker);
        if (index < 0) return "GENERAL";
        int start = index + marker.length();
        int end = description.indexOf('\n', start);
        return end < 0 ? description.substring(start).trim() : description.substring(start, end).trim();
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
