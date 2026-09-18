package io.github.chenyouxin8.chenaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private ToolObserver toolObserver;
    private long actualInputTokens;
    private long actualOutputTokens;
    private long modelCallCount;
    private String lastModel;

    @FunctionalInterface
    public interface ToolObserver {
        void onEvent(String phase, String toolName, String detail);
    }

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
    }

    public void setToolObserver(ToolObserver observer) {
        this.toolObserver = observer;
        if (availableTools == null || availableTools.length == 0 || observer == null) return;
        this.availableTools = wrapTools(availableTools, observer);
    }

    private ToolCallback[] wrapTools(ToolCallback[] tools, ToolObserver observer) {
        return java.util.Arrays.stream(tools)
                .map(delegate -> new ToolCallback() {
                    @Override
                    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                        return delegate.getToolDefinition();
                    }

                    @Override
                    public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                        return delegate.getToolMetadata();
                    }

                    @Override
                    public String call(String toolInput) {
                        String name = delegate.getToolDefinition().name();
                        observer.onEvent("started", name, truncate(toolInput));
                        try {
                            String result = delegate.call(toolInput);
                            observer.onEvent("completed", name, truncate(result));
                            return result;
                        } catch (RuntimeException e) {
                            observer.onEvent("failed", name, truncate(e.getMessage()));
                            throw e;
                        }
                    }

                    @Override
                    public String call(String toolInput, ToolContext toolContext) {
                        String name = delegate.getToolDefinition().name();
                        observer.onEvent("started", name, truncate(toolInput));
                        try {
                            String result = delegate.call(toolInput, toolContext);
                            observer.onEvent("completed", name, truncate(result));
                            return result;
                        } catch (RuntimeException e) {
                            observer.onEvent("failed", name, truncate(e.getMessage()));
                            throw e;
                        }
                    }
                })
                .toArray(ToolCallback[]::new);
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 800 ? value : value.substring(0, 800) + "...";
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
            recordUsage(chatResponse);
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String result = assistantMessage.getText();
            getMessageList().add(assistantMessage);

            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                log.info("{} think() => FALSE (no tool calls, ending)", getName());
                setLastThinkResult(result);
                setState(AgentState.FINISHED);
                return false;
            }

            log.info("{} think() => TRUE (has tool calls)", getName());
            setLastThinkResult(result);
            return true;
        } catch (Exception e) {
            log.error("{} think() 异常: {}", getName(), e.getMessage());
            setLastThinkResult("处理时遇到错误: " + e.getMessage());
            setState(AgentState.FINISHED);
            return false;
        }
    }

    private void recordUsage(ChatResponse response) {
        if (response == null) return;
        modelCallCount++;
        if (response.getMetadata() != null) lastModel = response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) return;
        actualInputTokens += safe(usage.getPromptTokens());
        actualOutputTokens += safe(usage.getCompletionTokens());
    }

    private long safe(Integer value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    @Override
    public String act() {
        return getLastThinkResult() != null ? getLastThinkResult() : "没有可返回的结果";
    }
}
