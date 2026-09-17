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

    public ChenManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("ChenManus");

        String systemPrompt = """
                You are ChenManus 2.0, a general-purpose task agent.
                Your goal is to complete the user's task using the available tools safely and efficiently.
                Break complex requests into practical actions, use tools when they improve the result,
                and provide a concise final result after execution.
                """;
        this.setSystemPrompt(systemPrompt);

        String nextStepPrompt = """
                Select the most appropriate tool or combination of tools for the current task.
                For complex tasks, reason about the next actionable step before calling a tool.
                After tool execution, inspect the result and continue until the user's goal is satisfied.
                If the task is complete, use the terminate tool/function call.
                """;
        this.setNextStepPrompt(nextStepPrompt);
        this.setMaxSteps(9);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    public String run(String userPrompt) {
        if (getState() != AgentState.IDLE) throw new IllegalStateException("Agent state must be IDLE, current: " + getState());
        if (userPrompt == null || userPrompt.isBlank()) throw new IllegalArgumentException("prompt cannot be blank");
        this.setState(AgentState.RUNNING);
        try {
            getMessageList().add(new org.springframework.ai.chat.messages.UserMessage(userPrompt));
            StringBuilder results = new StringBuilder();
            for (int i = 0; i < getMaxSteps(); i++) {
                this.setCurrentStep(i + 1);
                String stepResult = step();
                results.append(stepResult).append("\n");
                if (getState() == AgentState.FINISHED) break;
            }
            this.setState(AgentState.FINISHED);
            String output = results.toString().trim();
            return output.isEmpty() ? "没有执行结果" : output;
        } catch (Exception e) {
            this.setState(AgentState.ERROR);
            log.error("执行失败", e);
            return "执行失败: " + e.getMessage();
        } finally {
            cleanup();
        }
    }
}
