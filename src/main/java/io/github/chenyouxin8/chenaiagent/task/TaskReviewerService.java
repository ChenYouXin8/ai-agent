package io.github.chenyouxin8.chenaiagent.task;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class TaskReviewerService {

    private static final String SYSTEM_PROMPT = """
            You are the ChenManus 2.0 Reviewer.
            Evaluate whether the task result actually satisfies the user's goal.
            Return only a structured ReviewDecision object.
            Be conservative: pass only when the result is substantively complete.
            Mention concrete missing items when it fails.
            """;

    private final ChatClient chatClient;
    private final TaskMetricsService metricsService;

    public TaskReviewerService(ChatModel chatModel, TaskMetricsService metricsService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.metricsService = metricsService;
    }

    public ReviewDecision review(ChenTask task) {
        String outputs = task.getSteps().stream()
                .filter(step -> step.getOutput() != null && !step.getOutput().isBlank())
                .map(step -> "步骤 " + step.getSequence() + " - " + step.getTitle() + ":\n" + truncate(step.getOutput(), 3500))
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                用户原始任务：
                %s

                执行结果：
                %s

                请判断结果是否已经完成用户任务，并给出简洁反馈。
                """.formatted(task.getPrompt(), outputs.isBlank() ? "（没有有效执行结果）" : outputs);

        try {
            ResponseEntity<org.springframework.ai.chat.model.ChatResponse, ReviewDecision> response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .responseEntity(ReviewDecision.class, spec -> spec.validateSchema());
            org.springframework.ai.chat.model.ChatResponse chatResponse = response.getResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                metricsService.recordActualUsage(
                        task,
                        usage.getPromptTokens() == null ? 0L : usage.getPromptTokens(),
                        usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens(),
                        1L);
            }
            return response.getEntity() == null ? ReviewDecision.fallback("审核器未返回结果") : response.getEntity();
        } catch (Exception e) {
            return ReviewDecision.fallback("审核器暂不可用，已完成基础结果检查");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
