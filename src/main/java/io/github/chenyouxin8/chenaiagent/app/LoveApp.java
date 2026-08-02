package io.github.chenyouxin8.chenaiagent.app;

import io.github.chenyouxin8.chenaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 恋爱心理助手核心服务类
 * <p>
 * 基于 Spring AI ChatClient 提供三种能力：
 * <ul>
 *   <li>{@link #doChat}：带对话记忆的日常聊天</li>
 *   <li>{@link #doChatWithReport}：结构化输出恋爱报告</li>
 *   <li>{@link #doChatWithRag}：基于知识库（向量存储）的问答</li>
 * </ul>
 */
@Component
@Slf4j
public class LoveApp {
    private final ChatClient chatClient;

    /**
     * 系统提示词：定义 AI 的角色为恋爱心理专家，
     * 并按单身/恋爱/已婚三种状态引导用户描述问题。
     */
    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    /**
     * 构造 ChatClient：注入系统提示词 + 对话记忆顾问
     * @param dashscopeChatModel 大模型（DashScope）
     */
    public LoveApp(ChatModel dashscopeChatModel) {
        ChatMemory chatMemory = new FileBasedChatMemory(
                System.getProperty("user.dir") + "/.chat-memory"
        );


        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 日常聊天：携带对话记忆，按 chatId 区分会话
     * @param message 用户输入
     * @param chatId  对话ID（用于隔离各用户的记忆）
     * @return AI 回复文本
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                // 绑定会话记忆：会话ID + 最近 10 条上下文
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 10))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);

        return content;
    }

    /**
     * 恋爱报告数据结构：标题 + 建议列表
     */
    public record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     * 让模型按 {@link LoveReport} 结构返回结果
     * @param message 用户输入
     * @param chatId  对话ID
     * @return 结构化的恋爱报告
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .user(message)
                .call()
                .entity(LoveReport.class);

        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    /**
     * 知识库向量存储：由 Spring 容器注入（配置类创建 SimpleVectorStore Bean）
     */
    @Resource
    private VectorStore loveAppVectorStore;

    /**
     * RAG 问答：先从知识库检索相关资料，再让模型结合资料回答
     * @param message 用户输入
     * @param chatId  对话ID
     * @return 结合知识库的 AI 回复
     */
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                // 绑定会话记忆（修复：参数名与 doChat 保持一致）
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 10))
                // 应用知识库问答（Spring AI 2.0 使用 RetrievalAugmentationAdvisor + VectorStoreDocumentRetriever）
                .advisors(RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(VectorStoreDocumentRetriever.builder()
                                .vectorStore(loveAppVectorStore)
                                .build())
                        .build())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
