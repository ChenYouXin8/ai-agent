package io.github.chenyouxin8.chenaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * 日志顾问（CallAdvisor）
 * <p>
 * 在每次 ChatClient 调用前后打印日志：
 * <ul>
 *   <li>入参：用户输入内容</li>
 *   <li>出参：AI 回复内容</li>
 * </ul>
 * 仅用于观察调试，【不再】将历史消息重新塞给 LLM（会导致 Prompt 膨胀、Token 暴增）。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "MyLoggerAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 打印本次输入（不涉及历史消息）
        log.info(">>> 用户输入: {}", request.prompt().getContents());
        ChatClientResponse response = chain.nextCall(request);
        // 打印本次输出（response 可能为空时做防御）
        String reply = response != null
                && response.chatResponse() != null
                && response.chatResponse().getResult() != null
                && response.chatResponse().getResult().getOutput() != null
                ? response.chatResponse().getResult().getOutput().getText()
                : "(空回复)";
        log.info("<<< AI 回复: {}", reply);
        return response;
    }
}
