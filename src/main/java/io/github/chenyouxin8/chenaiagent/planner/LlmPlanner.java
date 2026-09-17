package io.github.chenyouxin8.chenaiagent.planner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class LlmPlanner {

    private static final String SYSTEM_PROMPT = """
            You are ChenManus 2.0 Planner.
            Convert the user's goal into a small, practical execution DAG for a general-purpose AI agent.
            Return only a structured Plan object.
            Rules:
            - Create 2 to 6 actionable steps.
            - Each step must have a short title, a concrete description, a simple type, and an expected output.
            - dependsOn contains 1-based step sequence numbers that must finish before the current step can run.
            - dependsOn must never reference the current step or a later step.
            - A step may have multiple dependencies when it combines independent work.
            - Mark parallelizable=true only when all its dependencies are satisfied and it is safe to run concurrently with other ready steps.
            - Typical parallelizable steps are independent research/source collection tasks; synthesis, coding that edits the same files, editing and final validation should normally be false.
            - Use types such as RESEARCH, ANALYSIS, CODE, DOCUMENT, FILE, WEB, GENERAL.
            - The plan must be useful even when the task does not need external tools.
            """;

    private final ChatClient chatClient;

    public LlmPlanner(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public Plan createPlan(String userPrompt) {
        return createPlanWithUsage(userPrompt).plan();
    }

    public PlanResult createPlanWithUsage(String userPrompt) {
        try {
            var response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Create an execution DAG for this user task:\n\n" + userPrompt)
                    .call()
                    .responseEntity(Plan.class, spec -> spec.validateSchema());

            Plan plan = response == null ? null : response.getEntity();
            Usage usage = usageOf(response == null ? null : response.getResponse());
            if (plan == null || plan.steps() == null) return new PlanResult(fallback(userPrompt), usageInput(usage), usageOutput(usage), usage == null ? 0 : 1);
            List<PlanStep> raw = plan.steps().stream()
                    .filter(step -> step != null && step.title() != null && !step.title().isBlank())
                    .limit(6)
                    .toList();
            if (raw.isEmpty()) return new PlanResult(fallback(userPrompt), usageInput(usage), usageOutput(usage), usage == null ? 0 : 1);

            List<PlanStep> normalized = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                PlanStep step = raw.get(i);
                Set<Integer> dependencies = new HashSet<>();
                if (step.dependsOn() != null) {
                    for (Integer dependency : step.dependsOn()) {
                        if (dependency != null && dependency >= 1 && dependency <= i) {
                            dependencies.add(dependency);
                        }
                    }
                }
                normalized.add(new PlanStep(
                        step.title().trim(),
                        step.description(),
                        step.type(),
                        step.expectedOutput(),
                        dependencies.stream().sorted().toList(),
                        step.parallelizable()
                ));
            }

            Plan normalizedPlan = new Plan(
                    plan.title() == null || plan.title().isBlank() ? "ChenManus 任务" : plan.title().trim(),
                    plan.summary() == null ? "" : plan.summary().trim(),
                    normalized
            );
            return new PlanResult(normalizedPlan, usageInput(usage), usageOutput(usage), usage == null ? 0 : 1);
        } catch (Exception e) {
            log.warn("LLM planner failed, using fallback plan: {}", e.getMessage());
            return new PlanResult(fallback(userPrompt), 0L, 0L, 0L);
        }
    }

    private Usage usageOf(ChatResponse response) {
        return response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
    }

    private long usageInput(Usage usage) {
        return usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens().longValue();
    }

    private long usageOutput(Usage usage) {
        return usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens().longValue();
    }

    private Plan fallback(String prompt) {
        return new Plan(
                prompt.length() > 32 ? prompt.substring(0, 32) + "..." : prompt,
                "模型规划不可用时使用的安全兜底计划",
                List.of(new PlanStep("完成用户任务", prompt, "GENERAL", "返回满足用户要求的最终结果", List.of(), false))
        );
    }

    public record PlanResult(
            Plan plan,
            long actualInputTokens,
            long actualOutputTokens,
            long modelCallCount
    ) {}
}
