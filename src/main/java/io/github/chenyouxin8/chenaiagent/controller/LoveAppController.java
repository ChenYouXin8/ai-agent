package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.app.LoveApp;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 恋爱专家 Agent HTTP 接口
 *
 * 将 LoveApp 的能力暴露为 REST 接口，让前端/外部系统可以调用。
 */
@RestController
@RequestMapping("/ai/love")
public class LoveAppController {

    private final LoveApp loveApp;

    // 通过 Spring 注入 LoveApp Bean（LoveApp 有 @Component 注解，已被 Spring 管理）
    public LoveAppController(LoveApp loveApp) {
        this.loveApp = loveApp;
    }

    /**
     * 恋爱专家对话接口
     *
     * GET /api/ai/love/chat?message=xxx&chatId=xxx
     *
     * @param message 用户输入
     * @param chatId  会话 ID（用于区分不同用户的对话历史）
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * 恋爱专家结构化报告接口
     *
     * POST /api/ai/love/report
     * Body: { "message": "帮我分析一下我的恋爱困境", "chatId": "user-001" }
     */
    @PostMapping("/report")
    public LoveApp.LoveReport report(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String chatId = body.getOrDefault("chatId", "default");
        return loveApp.doChatWithReport(message, chatId);
    }

    /**
     * 恋爱专家 RAG 知识库问答接口
     *
     * GET /api/ai/love/rag?message=xxx&chatId=xxx
     *
     * 会先从知识库检索相关文档，再结合文档回答
     */
    @GetMapping("/rag")
    public String rag(@RequestParam String message,
                      @RequestParam(defaultValue = "default") String chatId) {
        return loveApp.doChatWithRag(message, chatId);
    }
}
