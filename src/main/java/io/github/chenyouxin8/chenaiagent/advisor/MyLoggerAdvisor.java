package io.github.chenyouxin8.chenaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * 日志顾问（CallAdvisor）
 * <p>
 * 在每次对话调用前后打印日志：
 * <ul>
 *   <li>入参：用户输入内容</li>
 *   <li>出参：AI 回复内容</li>
 * </ul>
 * 便于观察带工具调用的对话效果。
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
        log.info(">>> 用户输入: {}", request.prompt().getContents());
        ChatClientResponse response = chain.nextCall(request);
        log.info("<<< AI 回复: {}", response.chatResponse().getResult().getOutput().getText());
        return response;
    }
}
