package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.agent.ChenManus;
import io.github.chenyouxin8.chenaiagent.planner.LlmPlanner;
import io.github.chenyouxin8.chenaiagent.planner.Plan;
import io.github.chenyouxin8.chenaiagent.planner.PlanStep;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
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
    private final AgentTeamPlannerService teamPlannerService;
    private final AgentHandoffService handoffService;
    private final TaskQueueService queueService;
    private final TaskMetricsService metricsService;
    private final Semaphore parallelSlots;

    public TaskRuntimeService(
            TaskManager taskManager,
            ToolCallback[] tools,
            ChatModel chatModel,
            LlmPlanner planner,
            TaskReviewerService reviewer,
            TaskMemoryService memoryService,
            ArtifactService artifactService,
            AgentRolePromptService rolePromptService,
            AgentTeamPlannerService teamPlannerService,
            AgentHandoffService handoffService,
            TaskQueueService queueService,
            TaskMetricsService metricsService,
            @Value("${chenmanus.runtime.max-parallel-steps:4}") int maxParallelSteps
    ) {
        this.taskManager = taskManager;
        this.tools = tools;
        this.chatModel = chatModel;
        this.planner = planner;
        this.reviewer = reviewer;
        this.memoryService = memoryService;
        this.artifactService = artifactService;
        this.rolePromptService = rolePromptService;
        this.teamPlannerService = teamPlannerService;
        this.handoffService = handoffService;
        this.queueService = queueService;
        this.metricsService = metricsService;
        this.parallelSlots = new Semaphore(Math.max(1, maxParallelSteps));
    }

    public void start(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) return;
        taskManager.updateStatus(task, TaskStatus.QUEUED, "任务已进入 ChenManus 执行队列");
        taskManager.publish(new TaskEvent(taskId, TaskEventType.TASK_QUEUED, null,
                queueService.isRedisEnabled() ? "Redis 分布式任务队列" : "本地任务队列"));
        queueService.enqueue(taskId, task.getPriority());
    }

    public void pause(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.QUEUED
                || task.getStatus() == TaskStatus.RUNNING
                || task.getStatus() == TaskStatus.PLANNING) {
            taskManager.updateStatus(task, TaskStatus.PAUSED, "任务已暂停，将在当前执行阶段结束后保持暂停状态");
        }
    }

    public void resume(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.PAUSED) start(taskId);
    }

    public void cancel(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() != TaskStatus.COMPLETED
                && task.getStatus() != TaskStatus.FAILED
                && task.getStatus() != TaskStatus.CANCELLED) {
            taskManager.updateStatus(task, TaskStatus.CANCELLED, "任务已取消");
        }
    }

    public void runNow(String taskId) {
        execute(taskManager.get(taskId));
    }

    private void execute(ChenTask task) {
        if (isStopped(task)
                || task.getStatus() == TaskStatus.COMPLETED
                || task.getStatus() == TaskStatus.FAILED) return;

        metricsService.startTask(task);
        taskManager.save(task);
        try {
            if (task.getSteps().isEmpty()) {
                taskManager.updateStatus(task, TaskStatus.PLANNING, "ChenManus 正在生成执行 DAG");
                createPlan(task);
                taskManager.save(task);
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.PLAN_CREATED, null,
                        "已生成 " + task.getSteps().size() + " 个执行步骤"));
            }

            if (!runPendingSteps(task) || isStopped(task) || task.getStatus() == TaskStatus.FAILED) return;

            taskManager.updateStatus(task, TaskStatus.REVIEWING, "Reviewer 正在验收任务结果");
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_STARTED, null, "开始结果审核"));
            ReviewDecision decision = reviewer.review(task);
            task.setReview(decision);
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_COMPLETED, null,
                    decision.feedback() == null ? "审核完成" : decision.feedback()));

            for (int repairRound = 0; !decision.passed() && repairRound < MAX_REPAIR_ROUNDS; repairRound++) {
                TaskStep repairStep = createRepairStep(task, decision);
                if (!runStepWithRetry(task, repairStep)) break;
                taskManager.updateStatus(task, TaskStatus.REVIEWING, "补救步骤完成，Reviewer 正在二次验收");
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_STARTED, null, "开始二次审核"));
                decision = reviewer.review(task);
                task.setReview(decision);
                taskManager.save(task);
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.REVIEW_COMPLETED, null,
                        decision.feedback() == null ? "二次审核完成" : decision.feedback()));
            }

            task.setResult(task.getSteps().stream()
                    .filter(step -> step.getOutput() != null && !step.getOutput().isBlank())
                    .map(step -> "【" + step.getTitle() + "】\n" + step.getOutput())
                    .collect(Collectors.joining("\n\n")));

            if (!decision.passed()) {
                task.setError("Reviewer 未通过：" + safe(decision.missingItems(), decision.feedback()));
                metricsService.finishTask(task);
                taskManager.save(task);
                memoryService.remember(task);
                taskManager.updateStatus(task, TaskStatus.FAILED, task.getError());
                return;
            }

            metricsService.finishTask(task);
            memoryService.remember(task);
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.METRICS_UPDATED, null,
                    formatMetrics(task)));
            taskManager.updateStatus(task, TaskStatus.COMPLETED, "任务完成并通过审核");
        } catch (Exception e) {
            task.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            metricsService.finishTask(task);
            taskManager.save(task);
            memoryService.remember(task);
            taskManager.updateStatus(task, TaskStatus.FAILED, "任务失败：" + task.getError());
        }
    }

    private boolean runPendingSteps(ChenTask task) {
        Map<Integer, TaskStep> bySequence = task.getSteps().stream()
                .collect(Collectors.toMap(TaskStep::getSequence, Function.identity()));

        while (true) {
            if (isStopped(task)) return false;

            List<TaskStep> pending = task.getSteps().stream()
                    .filter(step -> step.getStatus() != StepStatus.COMPLETED && step.getStatus() != StepStatus.SKIPPED)
                    .toList();
            if (pending.isEmpty()) return true;

            List<TaskStep> ready = pending.stream()
                    .filter(step -> dependenciesSatisfied(step, bySequence))
                    .toList();
            if (ready.isEmpty()) {
                task.setError("执行 DAG 无可运行步骤：存在循环依赖或缺失依赖");
                taskManager.save(task);
                taskManager.updateStatus(task, TaskStatus.FAILED, task.getError());
                return false;
            }

            List<TaskStep> parallel = ready.stream().filter(TaskStep::isParallelizable).toList();
            if (parallel.size() >= 2) {
                taskManager.updateStatus(task, TaskStatus.RUNNING,
                        "并行执行 " + parallel.size() + " 个 DAG 节点（本实例并发上限受配置控制）");
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.MESSAGE, null,
                        "并行启动：" + parallel.stream().map(TaskStep::getTitle).collect(Collectors.joining("、"))));

                List<CompletableFuture<Boolean>> futures = parallel.stream()
                        .map(step -> CompletableFuture.supplyAsync(() -> runParallelStep(step, task)))
                        .toList();
                boolean success = futures.stream().allMatch(CompletableFuture::join);
                if (!success) {
                    TaskStep failed = parallel.stream()
                            .filter(step -> step.getStatus() == StepStatus.FAILED)
                            .findFirst().orElse(parallel.get(0));
                    task.setError(failed.getError());
                    taskManager.updateStatus(task, TaskStatus.FAILED, "并行步骤失败：" + failed.getTitle());
                    return false;
                }
            } else {
                TaskStep current = ready.get(0);
                taskManager.updateStatus(task, TaskStatus.RUNNING, "执行：" + current.getTitle());
                if (!runStepWithRetry(task, current)) {
                    task.setError(current.getError());
                    taskManager.save(task);
                    taskManager.updateStatus(task, TaskStatus.FAILED, "步骤失败：" + current.getTitle());
                    return false;
                }
            }
        }
    }

    private boolean runParallelStep(TaskStep step, ChenTask task) {
        parallelSlots.acquireUninterruptibly();
        try {
            return runStepWithRetry(task, step);
        } finally {
            parallelSlots.release();
        }
    }

    private boolean dependenciesSatisfied(TaskStep step, Map<Integer, TaskStep> bySequence) {
        if (step.getDependsOn() == null || step.getDependsOn().isEmpty()) return true;
        return step.getDependsOn().stream().allMatch(sequence -> {
            TaskStep dependency = bySequence.get(sequence);
            return dependency != null
                    && (dependency.getStatus() == StepStatus.COMPLETED || dependency.getStatus() == StepStatus.SKIPPED);
        });
    }

    private void createPlan(ChenTask task) {
        String memoryContext = memoryService.recallContext(task.getTenantId(), task.getOwnerId(), task.getSessionId(), task.getPrompt(), 3);
        String planningPrompt = task.getPrompt();
        if (!memoryContext.isBlank()) planningPrompt += "\n\n以下是同一租户/用户/会话的历史任务记忆，仅用于参考：\n" + memoryContext;

        LlmPlanner.PlanResult planResult = planner.createPlanWithUsage(planningPrompt);
        metricsService.recordActualUsage(task,
                planResult.actualInputTokens(), planResult.actualOutputTokens(), planResult.modelCallCount());
        Plan plan = planResult.plan();
        task.setTitle(plan.title());
        task.setPlanSummary(plan.summary());
        for (PlanStep planStep : plan.steps()) {
            int sequence = task.getSteps().size() + 1;
            String description = safe(planStep.description(), "执行该计划步骤");
            String expected = safe(planStep.expectedOutput(), "完成该步骤并返回可验证的结果");
            String type = safe(planStep.type(), "GENERAL");
            List<Integer> dependencies = planStep.dependsOn() == null ? List.of() : planStep.dependsOn().stream()
                    .filter(dep -> dep != null && dep >= 1 && dep < sequence)
                    .distinct().sorted().toList();
            String enrichedDescription = "类型：" + type + "\n" + description + "\n预期输出：" + expected;
            TaskStep taskStep = new TaskStep(task.getTaskId() + "_step_" + sequence, sequence,
                    planStep.title().trim(), enrichedDescription, planStep.parallelizable(), dependencies);
            task.getSteps().add(taskStep);
            String dependencyText = dependencies.isEmpty() ? "无依赖" : "依赖 Step " + dependencies.stream().map(String::valueOf).collect(Collectors.joining(", "));
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_PLANNED,
                    taskStep.getStepId(), taskStep.getTitle() + "（" + dependencyText + (planStep.parallelizable() ? "，可并行" : "") + "）"));
        }
        task.touch();
    }

    private TaskStep createRepairStep(ChenTask task, ReviewDecision decision) {
        int sequence = task.getSteps().size() + 1;
        List<Integer> dependencies = task.getSteps().stream().map(TaskStep::getSequence).sorted().toList();
        String missing = safe(decision.missingItems(), "补足审核发现的缺失内容");
        String feedback = safe(decision.feedback(), "重新检查并修正最终结果");
        TaskStep repairStep = new TaskStep(task.getTaskId() + "_step_" + sequence, sequence,
                "补救与修正", "根据 Reviewer 反馈修正结果。\n缺失项：" + missing + "\n审核反馈：" + feedback,
                false, dependencies);
        task.getSteps().add(repairStep);
        taskManager.save(task);
        taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_PLANNED,
                repairStep.getStepId(), repairStep.getTitle()));
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
                    taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_RETRY,
                            step.getStepId(), "第 " + attempt + " 次重试：" + step.getTitle()));
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

        AgentAssignment assignment = teamPlannerService.assign(step);
        handoffService.publish(task, step, assignment, taskManager);
        taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_STARTED,
                step.getStepId(), step.getTitle() + " · " + assignment.role().name()));

        ChenManus agent = null;
        String prompt = buildStepPrompt(task, step, assignment);
        try {
            agent = new ChenManus(tools, chatModel);
            agent.setToolObserver((phase, toolName, detail) -> {
                TaskEventType eventType = switch (phase) {
                    case "started" -> TaskEventType.TOOL_STARTED;
                    case "completed" -> TaskEventType.TOOL_COMPLETED;
                    case "failed" -> TaskEventType.TOOL_FAILED;
                    default -> TaskEventType.MESSAGE;
                };
                taskManager.publish(new TaskEvent(task.getTaskId(), eventType, step.getStepId(),
                        toolName + (detail == null || detail.isBlank() ? "" : "：" + detail)));
            });
            String output = agent.run(prompt);
            if (output == null || output.isBlank()) throw new IllegalStateException("Agent 未返回有效结果");

            metricsService.recordStep(task, step, prompt, output);
            step.setOutput(output);
            step.setStatus(StepStatus.COMPLETED);
            step.setError(null);
            step.setDurationMs(Math.max(0L, System.currentTimeMillis() - step.getStartedAt()));
            synchronized (task) { artifactService.capture(task, output, taskManager); }
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_COMPLETED,
                    step.getStepId(), output));
        } catch (Exception e) {
            step.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            step.setStatus(StepStatus.FAILED);
            step.setDurationMs(Math.max(0L, System.currentTimeMillis() - step.getStartedAt()));
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_FAILED,
                    step.getStepId(), step.getError()));
            throw e;
        } finally {
            if (agent != null) {
                metricsService.recordActualUsage(task, step,
                        agent.getActualInputTokens(), agent.getActualOutputTokens(), agent.getModelCallCount());
            }
            step.setCompletedAt(System.currentTimeMillis());
            task.touch();
            taskManager.save(task);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.METRICS_UPDATED,
                    step.getStepId(), formatStepMetrics(step)));
        }
    }

    private String buildStepPrompt(ChenTask task, TaskStep step, AgentAssignment assignment) {
        String handoff = handoffService.buildHandoff(task, step, assignment);
        String memoryContext = memoryService.recallContext(task.getTenantId(), task.getOwnerId(), task.getSessionId(), task.getPrompt(), 2);

        return """
                你是 ChenManus 2.0 团队中的执行 Agent。
                %s

                当前总任务：%s
                当前执行步骤：%s
                步骤说明：%s

                团队交接上下文：
                %s

                执行规则：
                1. 只关注当前步骤，但必须使用团队交接上下文中的依赖结果。
                2. 历史记忆只用于参考，不得把其中内容当作当前事实。
                3. 能使用工具时优先使用工具完成实际工作，而不是只给建议。
                4. 不要伪造工具执行结果；无法完成时明确说明原因。
                5. 完成后返回清晰、可验证的结果，并说明未解决的问题。

                同一用户/会话历史任务记忆：
                %s
                """.formatted(
                assignment.instruction(), task.getPrompt(), step.getTitle(), step.getDescription(),
                handoff, memoryContext.isBlank() ? "（无）" : memoryContext);
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

    private String formatStepMetrics(TaskStep step) {
        String input = step.getActualInputTokens() > 0 ? String.valueOf(step.getActualInputTokens()) : "~" + step.getEstimatedInputTokens();
        String output = step.getActualOutputTokens() > 0 ? String.valueOf(step.getActualOutputTokens()) : "~" + step.getEstimatedOutputTokens();
        return "耗时 " + step.getDurationMs() + "ms，输入 " + input + " tokens，输出 " + output
                + " tokens，模型调用 " + step.getModelCallCount() + " 次";
    }

    private String formatMetrics(ChenTask task) {
        String input = task.getActualInputTokens() > 0 ? String.valueOf(task.getActualInputTokens()) : "~" + task.getEstimatedInputTokens();
        String output = task.getActualOutputTokens() > 0 ? String.valueOf(task.getActualOutputTokens()) : "~" + task.getEstimatedOutputTokens();
        return "耗时 " + task.getDurationMs() + "ms，输入 " + input + " tokens，输出 " + output
                + " tokens，模型调用 " + task.getModelCallCount() + " 次，成本 " + task.getEstimatedCost();
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
