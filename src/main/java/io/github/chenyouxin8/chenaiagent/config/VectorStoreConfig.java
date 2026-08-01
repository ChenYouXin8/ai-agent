package io.github.chenyouxin8.chenaiagent.config;

import io.github.chenyouxin8.chenaiagent.rag.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 知识库向量存储配置
 *
 * 当前使用内存向量存储（SimpleVectorStore），适合本地开发。
 * 生产环境可替换为：Milvus / Qdrant / Pgvector 等持久化向量数据库。
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    @Resource
    private EmbeddingModel embeddingModel;

    /**
     * 创建内存向量存储 Bean
     * - EmbeddingModel：从文本生成向量（用于 RAG 检索）
     * - SimpleVectorStore：内存存储，开发测试用
     */
    @Bean
    public VectorStore loveAppVectorStore(LoveAppDocumentLoader documentLoader) {
        log.info("初始化知识库向量存储（内存模式）");
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel)
                .build();

        // 启动时自动加载知识库文档并写入向量存储
        List<Document> documents = documentLoader.loadMarkdowns();
        if (!documents.isEmpty()) {
            log.info("加载知识库文档 {} 篇到向量存储", documents.size());
            vectorStore.add(documents);
        } else {
            log.warn("未加载到任何知识库文档，向量存储为空");
        }
        return vectorStore;
    }
}
