package io.github.chenyouxin8.chenaiagent.task;

import io.github.chenyouxin8.chenaiagent.agent.ChenManus;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TaskRuntimeService {
    private final TaskManager taskManager;
    private final ToolCallback[] tools;
    private final ChatModel chatModel;

    public TaskRuntimeService(TaskManager taskManager, ToolCallback[] tools, ChatModel chatModel) {
        this.taskManager = taskManager;
        this.tools = tools;
        this.chatModel = chatModel;
    }

    public void start(String taskId) { CompletableFuture.runAsync(() -> execute(taskManager.get(taskId))); }

    public void pause(String taskId) {
        ChenTask task = taskManager.get(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) taskManager.updateStatus(task, TaskStatus.PAUSED, "任务已暂停，将在当前 Agent 执行阶段结束后保持暂停状态");
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
        if (task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.FAILED) taskManager.updateStatus(task, TaskStatus.CANCELLED, "任务已取消");
    }

    private void execute(ChenTask task) {
        if (task.getStatus() == TaskStatus.CANCELLED) return;
        try {
            // Resume uses the existing plan instead of creating duplicate steps.
            if (task.getSteps().isEmpty()) {
                taskManager.updateStatus(task, TaskStatus.PLANNING, "正在分析任务并生成执行计划");
                createPlan(task);
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.PLAN_CREATED, null, "已生成 " + task.getSteps().size() + " 个执行阶段"));
                completePlanningMilestones(task);
            }
            if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.PAUSED) return;

            TaskStep executionStep = task.getSteps().stream().filter(s -> "执行 Agent".equals(s.getTitle())).findFirst().orElseThrow();
            if (executionStep.getStatus() != StepStatus.COMPLETED) {
                taskManager.updateStatus(task, TaskStatus.RUNNING, "ChenManus 开始执行核心任务");
                runAgent(task, executionStep);
            }

            if (task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.PAUSED) return;
            taskManager.updateStatus(task, TaskStatus.REVIEWING, "正在检查执行结果");
            TaskStep reviewStep = task.getSteps().stream().filter(s -> "验收结果".equals(s.getTitle())).findFirst().orElse(null);
            if (reviewStep != null && reviewStep.getStatus() != StepStatus.COMPLETED) {
                reviewStep.setStatus(StepStatus.COMPLETED);
                reviewStep.setOutput("基础验收通过：Agent 已返回执行结果");
                taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_COMPLETED, reviewStep.getStepId(), reviewStep.getOutput()));
            }
            task.setResult(task.getSteps().stream().map(TaskStep::getOutput).filter(s -> s != null && !s.isBlank()).reduce("", (a, b) -> a + b + "\n").trim());
            taskManager.updateStatus(task, TaskStatus.COMPLETED, "任务完成");
        } catch (Exception e) {
            task.setError(e.getMessage());
            taskManager.updateStatus(task, TaskStatus.FAILED, "任务失败: " + e.getMessage());
        }
    }

    private void completePlanningMilestones(ChenTask task) {
        for (TaskStep step : task.getSteps()) {
            if ("执行 Agent".equals(step.getTitle()) || "验收结果".equals(step.getTitle())) continue;
            step.setStatus(StepStatus.COMPLETED);
            step.setOutput("计划阶段已完成，具体工具调用由 ChenManus 在执行阶段自主决定");
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_COMPLETED, step.getStepId(), step.getOutput()));
        }
    }

    private void createPlan(ChenTask task) {
        String prompt = task.getPrompt().toLowerCase();
        addStep(task, "理解任务", "分析用户目标和约束");
        if (containsAny(prompt, "搜索", "研究", "资料", "市场", "新闻", "网页", "调研")) addStep(task, "收集信息", "使用可用的 Web / MCP 工具获取资料");
        if (containsAny(prompt, "代码", "项目", "bug", "开发", "程序", "java", "vue")) addStep(task, "分析或编写代码", "使用文件和终端工具完成开发工作");
        if (containsAny(prompt, "报告", "pdf", "文档", "总结", "整理")) addStep(task, "整理结果", "组织执行结果并生成可交付内容");
        addStep(task, "执行 Agent", "由 ChenManus 调度现有工具完成核心任务");
        addStep(task, "验收结果", "检查任务是否完成并整理最终输出");
    }

    private void addStep(ChenTask task, String title, String description) {
        int sequence = task.getSteps().size() + 1;
        task.getSteps().add(new TaskStep(task.getTaskId() + "_step_" + sequence, sequence, title, description));
    }

    private void runAgent(ChenTask task, TaskStep step) {
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(System.currentTimeMillis());
        taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_STARTED, step.getStepId(), step.getTitle()));
        try {
            ChenManus agent = new ChenManus(tools, chatModel);
            String output = agent.run(task.getPrompt());
            step.setOutput(output);
            step.setStatus(StepStatus.COMPLETED);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_COMPLETED, step.getStepId(), output));
        } catch (Exception e) {
            step.setError(e.getMessage());
            step.setStatus(StepStatus.FAILED);
            taskManager.publish(new TaskEvent(task.getTaskId(), TaskEventType.STEP_FAILED, step.getStepId(), e.getMessage()));
            throw e;
        } finally {
            step.setCompletedAt(System.currentTimeMillis());
            task.touch();
        }
    }

    private boolean containsAny(String text, String... values) { for (String value : values) if (text.contains(value)) return true; return false; }
}
