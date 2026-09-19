package io.github.chenyouxin8.chenaiagent.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用向量存储：用内存版 {@link SimpleVectorStore} 替代 Chroma，
 * 配合 ChenAiAgentApplicationTests 中排除 ChromaVectorStoreAutoConfiguration 使用，
 * 保证 Spring 上下文在无外部向量库时也能离线加载（TaskMemoryService 依赖 VectorStore）。
 * 嵌入模型为确定性零向量，不访问任何外部 API。
 */
@Configuration
public class TestVectorStoreConfig {

    private static final int SMOKE_DIMENSION = 16;

    @Bean
    VectorStore testVectorStore() {
        EmbeddingModel noopEmbeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<?> instructions = request.getInstructions() instanceof List<?> list ? list : List.of();
                List<Embedding> embeddings = new ArrayList<>(instructions.size());
                for (int i = 0; i < instructions.size(); i++) {
                    embeddings.add(new Embedding(new float[SMOKE_DIMENSION], i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return new float[SMOKE_DIMENSION];
            }

            @Override
            public int dimensions() {
                return SMOKE_DIMENSION;
            }
        };
        return SimpleVectorStore.builder(noopEmbeddingModel).build();
    }
}
