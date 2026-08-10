package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.app.LoveApp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

/**
 * 恋爱专家 SSE 流式对话控制器
 *
 * 提供 /ai/love/chat/sse 流式端点，前端 SSE 客户端可通过此接口
 * 获取恋爱专家的流式回复（打字机效果）。
 *
 * SSE 事件格式：
 *   data:{"step":1,"content":"..."}   ← 正常输出片段
 *   data:{"error":false,"message":""}  ← 完成
 *   data:{"error":true,"message":"..."} ← 错误
 */
@Slf4j
@RestController
@RequestMapping("/ai/love")
public class LoveAppSseController {

    private final LoveApp loveApp;

    public LoveAppSseController(LoveApp loveApp) {
        this.loveApp = loveApp;
    }

    /**
     * 恋爱专家流式对话（SSE）
     *
     * @param message 用户消息
     * @param chatId  对话 ID，默认 "default"
     */
    @GetMapping("/chat/sse")
    public SseEmitter chatSse(@RequestParam String message,
                              @RequestParam(defaultValue = "default") String chatId) {
        // 参数校验
        if (message == null || message.isBlank()) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":true,\"message\":\"消息内容不能为空\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // 5 分钟超时
        SseEmitter emitter = new SseEmitter(300_000L);

        // 异步执行 AI 对话，结果通过 SSE 推送
        new Thread(() -> {
            try {
                log.info("[LoveApp SSE] chatId={}, message={}", chatId, message);

                // 调用 LoveApp 获取回复（同步）
                String reply = loveApp.doChat(message, chatId);

                // 分段推送，模拟打字机效果（每 20 字符一个片段）
                int chunkSize = 20;
                int i = 0;
                while (i < reply.length()) {
                    int end = Math.min(i + chunkSize, reply.length());
                    String chunk = reply.substring(i, end);
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("{\"step\":1,\"content\":\"" + escapeJson(chunk) + "\"}"));
                    i = end;
                    // 打字机间隔 30ms，避免推送过快
                    Thread.sleep(30);
                }

                // 完成
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"error\":false,\"message\":\"\"}"));
                emitter.complete();

            } catch (Exception e) {
                log.error("[LoveApp SSE] 异常 chatId={}", chatId, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":true,\"message\":\"" + escapeJson(e.getMessage()) + "\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * JSON 字符串转义（防止 SSE data 中的特殊字符破坏 JSON）
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
