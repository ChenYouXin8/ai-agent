package io.github.chenyouxin8.chenaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 * <p>
 * 在 Spring AI 2.0.0 中，ChatClient 会自动处理工具调用循环，
 * 因此 think() 使用 ChatClient 完成包含工具调用在内的完整推理过程，
 * act() 返回 think() 阶段已经得到的结果。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存了工具调用信息的响应
    private ChatResponse toolCallChatResponse;

    // 上一步思考的结果（供 act() 使用）
    private String lastThinkResult;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
    }

    /**
     * 处理当前状态并决定下一步行动
     * 使用 ChatClient 进行推理，ChatClient 会自动处理工具调用循环
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        List<Message> messageList = getMessageList();
        try {
            // 使用 ChatClient 进行推理（Spring AI 2.0.0 会自动处理工具调用循环）
            ChatResponse chatResponse = getChatClient().prompt()
                    .messages(messageList)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            // 记录响应
            this.toolCallChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String result = assistantMessage.getText();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            log.info(getName() + "的思考: " + result);
            if (toolCallList != null && !toolCallList.isEmpty()) {
                log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
                String toolCallInfo = toolCallList.stream()
                        .map(toolCall -> String.format("工具名称：%s，参数：%s",
                                toolCall.name(),
                                toolCall.arguments())
                        )
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
            }

            // 记录助手消息到上下文
            getMessageList().add(assistantMessage);

            // 检查是否调用了终止工具
            if (toolCallList != null && toolCallList.stream()
                    .anyMatch(tc -> "doTerminate".equals(tc.name()))) {
                setState(AgentState.FINISHED);
                log.info(getName() + " 调用了终止工具，任务结束");
            }

            // 保存思考结果供 act() 使用
            this.lastThinkResult = result;

            // Spring AI 2.0.0 的 ChatClient 已在 call() 中自动执行工具调用循环，
            // 无需单独的 act() 步骤，返回 false 表示无需额外行动
            return false;
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题: " + e.getMessage());
            getMessageList().add(
                    new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            this.lastThinkResult = "处理时遇到错误: " + e.getMessage();
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     * 在 Spring AI 2.0.0 中，ChatClient 已在 think() 阶段自动完成工具调用，
     * 此方法返回 think() 阶段已经得到的结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        // 工具调用已在 think() 阶段由 ChatClient 自动完成
        return lastThinkResult != null ? lastThinkResult : "没有可返回的结果";
    }
}
