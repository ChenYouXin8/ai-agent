package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.app.LoveApp;
import io.github.chenyouxin8.chenaiagent.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai/love")
public class LoveAppController {

    private final LoveApp loveApp;

    public LoveAppController(LoveApp loveApp) {
        this.loveApp = loveApp;
    }

    /** 恋爱专家对话（带记忆） */
    @GetMapping("/chat")
    public ApiResponse<String> chat(@RequestParam String message,
                                    @RequestParam(defaultValue = "default") String chatId) {
        // 参数校验：message 不能为空
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String reply = loveApp.doChat(message, chatId);
        return ApiResponse.ok(reply);
    }

    /** 恋爱专家结构化报告 */
    @PostMapping("/report")
    public ApiResponse<LoveApp.LoveReport> report(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String chatId = body.getOrDefault("chatId", "default");
        LoveApp.LoveReport report = loveApp.doChatWithReport(message, chatId);
        return ApiResponse.ok(report);
    }

    /** 恋爱专家 RAG 知识库问答 */
    @GetMapping("/rag")
    public ApiResponse<String> rag(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String chatId) {
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String reply = loveApp.doChatWithRag(message, chatId);
        return ApiResponse.ok(reply);
    }

    /** 恋爱专家工具调用对话（AI 可调用文件/搜索/PDF 等工具） */
    @GetMapping("/tools")
    public ApiResponse<String> tools(@RequestParam String message,
                                     @RequestParam(defaultValue = "default") String chatId) {
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String reply = loveApp.doChatWithTools(message, chatId);
        return ApiResponse.ok(reply);
    }

    /** 恋爱专家 + MCP 工具调用（接 mcp-servers.json 配置的外部服务，如高德地图/图片搜索） */
    @GetMapping("/mcp")
    public ApiResponse<String> mcp(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String chatId) {
        if (message == null || message.isBlank()) {
            return ApiResponse.badRequest("消息内容不能为空");
        }
        String reply = loveApp.doChatWithMcp(message, chatId);
        return ApiResponse.ok(reply);
    }
}
