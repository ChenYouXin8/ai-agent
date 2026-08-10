package io.github.chenyouxin8.chenaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
    }

    @Override
    public boolean think() {
        try {
            ChatResponse chatResponse = getChatClient().prompt()
                    .user(getNextStepPrompt())
                    .system(getSystemPrompt())
                    .messages(getMessageList().toArray(new Message[0]))
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String result = assistantMessage.getText();

            // 将 assistant 回复加入历史
            getMessageList().add(assistantMessage);

            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("{} think() => FALSE (no tool calls, ending)", getName());
                setLastThinkResult(result);
                setState(AgentState.FINISHED);
                return false;
            } else {
                log.info("{} think() => TRUE (has tool calls)", getName());
                setLastThinkResult(result);
                return true;
            }
        } catch (Exception e) {
            log.error("{} think() 异常: {}", getName(), e.getMessage());
            setLastThinkResult("处理时遇到错误: " + e.getMessage());
            setState(AgentState.FINISHED);
            return false;
        }
    }

    @Override
    public String act() {
        return getLastThinkResult() != null ? getLastThinkResult() : "没有可返回的结果";
    }
}