package io.github.chenyouxin8.chenaiagent.rag;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LoveAppDocumentLoaderTest {

    @Test
    void loadMarkdowns_ShouldLoadAllMarkdownDocuments() {
        // Given
        LoveAppDocumentLoader loader = new LoveAppDocumentLoader(new PathMatchingResourcePatternResolver());

        // When
        List<Document> documents = loader.loadMarkdowns();

        // Then
        assertNotNull(documents);
        assertFalse(documents.isEmpty());
    }

    @Test
    void loadMarkdowns_ShouldAttachFilenameMetadata() {
        // Given
        LoveAppDocumentLoader loader = new LoveAppDocumentLoader(new PathMatchingResourcePatternResolver());

        // When
        List<Document> documents = loader.loadMarkdowns();

        // Then - 每个文件读取的 Document 都应带 filename 元数据
        assertNotNull(documents);
        assertFalse(documents.isEmpty());
        assertTrue(documents.stream()
                .anyMatch(doc -> doc.getMetadata().containsKey("filename")));
    }

    @Test
    void loadMarkdowns_ShouldThrowIllegalStateExceptionWhenResourceLoadFails() throws Exception {
        // Given - 模拟资源解析失败
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenThrow(new IOException("模拟读取失败"));
        LoveAppDocumentLoader loader = new LoveAppDocumentLoader(resolver);

        // 临时静默 LoveAppDocumentLoader 的 ERROR 日志，避免测试输出被预期的异常日志污染
        Logger loaderLogger = (Logger) LoggerFactory.getLogger(LoveAppDocumentLoader.class);
        Level originalLevel = loaderLogger.getLevel();
        loaderLogger.setLevel(Level.OFF);
        try {
            // When / Then
            IllegalStateException ex = assertThrows(IllegalStateException.class, loader::loadMarkdowns);
            assertEquals("Markdown 文档加载失败", ex.getMessage());
            assertInstanceOf(IOException.class, ex.getCause());
            verify(resolver).getResources("classpath:document/*.md");
        } finally {
            // 恢复原日志级别
            loaderLogger.setLevel(originalLevel);
        }
    }
}
