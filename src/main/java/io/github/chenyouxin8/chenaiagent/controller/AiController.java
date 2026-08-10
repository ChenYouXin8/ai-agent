package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.agent.ChenManus;
import io.github.chenyouxin8.chenaiagent.app.LoveApp;
import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话控制器
 *
 * 提供通用对话与 Manus 智能助手两条路由。
 * 所有接口均返回 ApiResponse 统一格式，SSE 接口直接写流并通过 emitter 处理错误。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private ToolCallback[] toolCallbacks;

    @Resource
    private LoveApp loveApp;

    /**
     * 通用对话（LoveApp 恋爱专家）
     */
    @GetMapping("/chat")
    public ApiResponse<String> doChat(@RequestParam String message) {
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String response = loveApp.doChat(message, "default");
        return ApiResponse.ok(response);
    }

    /**
     * Manus 智能助手（SSE 流式输出）
     *
     * 错误通过 emitter.send() 返回 JSON 格式，客户端可按 data:{"error":true,"message":"..."} 解析。
     * 超时 5 分钟自动断开。
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(@RequestParam String message) {
        if (message == null || message.isBlank()) {
            // 返回一个立即完成的 emitter，带错误信息
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":true,\"message\":\"消息内容不能为空\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // 每次请求 new ChenManus 实例，避免状态跨请求共享
        ChenManus chenManus = new ChenManus(toolCallbacks, dashscopeChatModel);

        return chenManus.runStream(message);
    }
}