package io.github.chenyouxin8.chenaiagent.agent;

import io.github.chenyouxin8.chenaiagent.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChenManus extends ToolCallAgent {

    public ChenManus(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel
    ) {
        super(allTools);
        this.setName("yuManus");

        String SYSTEM_PROMPT = """
                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(9);

        // 不用 MessageChatMemoryAdvisor，直接手动管理 messageList
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    public String run(String userPrompt) {
        if (getState() != AgentState.IDLE) {
            throw new IllegalStateException("Agent state must be IDLE, current: " + getState());
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be blank");
        }
        this.setState(AgentState.RUNNING);

        try {
            // 第一步：把用户消息加入历史
            getMessageList().add(new org.springframework.ai.chat.messages.UserMessage(userPrompt));

            StringBuilder results = new StringBuilder();
            for (int i = 0; i < getMaxSteps(); i++) {
                int stepNum = i + 1;
                this.setCurrentStep(stepNum);
                log.info("Step {} / {}", stepNum, getMaxSteps());

                String stepResult = step();
                results.append(stepResult);
                results.append("\n");
            }

            this.setState(AgentState.FINISHED);
            String output = results.toString().trim();
            if (output.isEmpty()) {
                return "没有执行结果";
            }
            return output;
        } catch (Exception e) {
            this.setState(AgentState.ERROR);
            log.error("执行失败", e);
            return "执行失败: " + e.getMessage();
        } finally {
            cleanup();
        }
    }
}