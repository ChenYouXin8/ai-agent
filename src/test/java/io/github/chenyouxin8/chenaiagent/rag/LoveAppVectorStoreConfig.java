package io.github.chenyouxin8.chenaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用向量存储配置：用内存版 {@link SimpleVectorStore} 替代 Chroma，避免单测依赖外部向量库。
 *
 * <ul>
 *   <li>local profile（集成测试，如 ChenManusTest）：使用真实 DashScope 嵌入模型，并在启动时预加载知识库文档；</li>
 *   <li>默认 profile（如 contextLoads 冒烟测试）：使用确定性零向量嵌入模型，不访问任何外部 API，保证上下文可离线加载。</li>
 * </ul>
 */
@Configuration
public class LoveAppVectorStoreConfig {

    private static final int SMOKE_DIMENSION = 16;

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    /**
     * 本地集成测试：真实嵌入模型 + 预加载文档。
     */
    @Bean(name = "loveAppVectorStore")
    @Profile("local")
    VectorStore localVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        simpleVectorStore.add(documents);
        return simpleVectorStore;
    }

    /**
     * 冒烟测试：零向量嵌入模型，不联网；知识库文档由生产侧 VectorStoreConfig 以零向量写入。
     */
    @Bean(name = "loveAppVectorStore")
    @Profile("!local")
    VectorStore smokeVectorStore() {
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
