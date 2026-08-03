package io.github.chenyouxin8.chenaiagent.controller;

import io.github.chenyouxin8.chenaiagent.tools.WebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final ChatClient chatClient;

    public AiController(ChatClient.Builder builder, WebSearchTool webSearchTool) {
        this.chatClient = builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultTools(webSearchTool)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
