package io.github.chenyouxin8.chenaiagent.planner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class LlmPlanner {

    private static final String SYSTEM_PROMPT = """
            You are ChenManus 2.0 Planner.
            Convert the user's goal into a small, practical execution plan for a general-purpose AI agent.
            Return only a structured Plan object.
            Rules:
            - Create 2 to 6 actionable steps.
            - Each step must have a short title, a concrete description, a simple type, and an expected output.
            - Do not create meta steps such as 'think', 'understand task', or 'call the agent'.
            - Prefer dependencies that can be executed sequentially.
            - Mark a step parallelizable=true only when it is independent from the immediately adjacent steps and can safely run at the same time.
            - Typical parallelizable steps are independent research/source collection tasks; synthesis, coding, editing and final validation should normally be false.
            - Use types such as RESEARCH, ANALYSIS, CODE, DOCUMENT, FILE, WEB, GENERAL.
            - The plan must be useful even when the task does not need external tools.
            """;

    private final ChatClient chatClient;

    public LlmPlanner(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public Plan createPlan(String userPrompt) {
        try {
            Plan plan = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Create an execution plan for this user task:\n\n" + userPrompt)
                    .call()
                    .entity(Plan.class, spec -> spec.validateSchema());

            if (plan == null || plan.steps() == null) return fallback(userPrompt);
            List<PlanStep> steps = plan.steps().stream()
                    .filter(step -> step != null && step.title() != null && !step.title().isBlank())
                    .limit(6)
                    .toList();
            if (steps.isEmpty()) return fallback(userPrompt);

            return new Plan(
                    plan.title() == null || plan.title().isBlank() ? "ChenManus 任务" : plan.title().trim(),
                    plan.summary() == null ? "" : plan.summary().trim(),
                    steps
            );
        } catch (Exception e) {
            log.warn("LLM planner failed, using fallback plan: {}", e.getMessage());
            return fallback(userPrompt);
        }
    }

    private Plan fallback(String prompt) {
        return new Plan(
                prompt.length() > 32 ? prompt.substring(0, 32) + "..." : prompt,
                "模型规划不可用时使用的安全兜底计划",
                List.of(new PlanStep("完成用户任务", prompt, "GENERAL", "返回满足用户要求的最终结果", false))
        );
    }
}
