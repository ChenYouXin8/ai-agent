package io.github.chenyouxin8.chenaiagent.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;
    private AgentState state = AgentState.IDLE;
    private int maxSteps = 9;
    private int currentStep = 0;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();
    private String lastThinkResult;

    public BaseAgent() {}

    public abstract String step();

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public void cleanup() {}

    /**
     * SSE 流式执行：每次 step 通过 emitter.send() 推送。
     * 错误时发送 JSON 格式 {"error":true,"message":"..."}，客户端可解析。
     * 超时 5 分钟自动断开。
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟

        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":true,\"message\":\"无法从状态运行代理: " + this.state + "\"}"));
                    emitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":true,\"message\":\"不能使用空提示词进行代理\"}"));
                    emitter.complete();
                    return;
                }

                this.state = AgentState.RUNNING;
                this.messageList.add(new UserMessage(userPrompt));

                try {
                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        this.currentStep = stepNumber;
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        String stepResult = step();
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data("{\"step\":" + stepNumber + ",\"content\":\"" +
                                        stepResult.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") +
                                        "\"}"));
                    }

                    if (this.currentStep >= this.maxSteps) {
                        this.state = AgentState.FINISHED;
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("{\"error\":false,\"message\":\"执行结束: 达到最大步数(" + this.maxSteps + ")\"}"));
                    }
                    emitter.complete();
                } catch (Exception e) {
                    this.state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":true,\"message\":\"执行错误: " +
                                        e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") +
                                        "\"}"));
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                this.cleanup();
            }
        });

        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE 连接超时");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE 连接完成");
        });

        return emitter;
    }
}