package io.github.chenyouxin8.chenaiagent.config;

import io.github.chenyouxin8.chenaiagent.rag.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 知识库向量存储初始化配置
 *
 * 启动时将 document/*.md 文档加载并写入向量存储（幂等操作）。
 * 通过检查向量存储中是否已有文档，避免重复写入。
 *
 * Chroma 需要先启动：chroma run --host 127.0.0.1 --port 8000
 */
@Configuration
@Slf4j
public class VectorStoreConfig implements InitializingBean {

    private static final int BATCH_SIZE = 10;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private LoveAppDocumentLoader documentLoader;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("检查并初始化 Chroma 向量存储...");

        List<Document> documents = documentLoader.loadMarkdowns();
        if (documents.isEmpty()) {
            log.warn("未加载到任何文档，请检查 resources/document/ 目录");
            return;
        }

        log.info("加载知识库文档 {} 篇（分批写入，每批最多 {} 篇）", documents.size(), BATCH_SIZE);

        // DashScope text-embedding-v3 每次批量上限为 10，手动分批
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            log.info("已写入第 {}-{} 篇文档", i + 1, end);
        }

        log.info("知识库文档写入完成");
    }
}