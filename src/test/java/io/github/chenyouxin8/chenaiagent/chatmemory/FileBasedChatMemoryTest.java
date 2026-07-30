package io.github.chenyouxin8.chenaiagent.chatmemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileBasedChatMemoryTest {

    private Path tempDir;
    private FileBasedChatMemory chatMemory;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("chat-memory-test-");
        chatMemory = new FileBasedChatMemory(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var files = Files.walk(tempDir)) {
            files.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void shouldAddAndGetMessages() {
        // Given
        String chatId = "chat-001";
        List<Message> messages = List.of(
                new UserMessage("你好"),
                new AssistantMessage("你好！有什么可以帮助你的吗？")
        );

        // When
        chatMemory.add(chatId, messages);
        List<Message> result = chatMemory.get(chatId);

        // Then
        assertEquals(2, result.size());
        assertEquals("你好", ((UserMessage) result.get(0)).getText());
        assertEquals("你好！有什么可以帮助你的吗？", ((AssistantMessage) result.get(1)).getText());
    }

    @Test
    void shouldReturnEmptyListForNonExistentConversation() {
        // When
        List<Message> result = chatMemory.get("non-existent");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetLastNMessages() {
        // Given
        String chatId = "chat-002";
        chatMemory.add(chatId, List.of(new UserMessage("msg1")));
        chatMemory.add(chatId, List.of(new UserMessage("msg2")));
        chatMemory.add(chatId, List.of(new UserMessage("msg3")));

        // When
        List<Message> last2 = chatMemory.get(chatId, 2);

        // Then
        assertEquals(2, last2.size());
        assertEquals("msg2", ((UserMessage) last2.get(0)).getText());
        assertEquals("msg3", ((UserMessage) last2.get(1)).getText());
    }

    @Test
    void shouldReturnAllMessagesWhenLastNExceedsSize() {
        // Given
        String chatId = "chat-003";
        chatMemory.add(chatId, List.of(new UserMessage("msg1")));
        chatMemory.add(chatId, List.of(new UserMessage("msg2")));

        // When
        List<Message> result = chatMemory.get(chatId, 10);

        // Then
        assertEquals(2, result.size());
    }

    @Test
    void shouldClearConversation() {
        // Given
        String chatId = "chat-004";
        chatMemory.add(chatId, List.of(new UserMessage("hello")));
        assertFalse(chatMemory.get(chatId).isEmpty());

        // When
        chatMemory.clear(chatId);

        // Then
        assertTrue(chatMemory.get(chatId).isEmpty());
    }

    @Test
    void shouldIsolateDifferentConversations() {
        // Given
        String chatId1 = "user-001";
        String chatId2 = "user-002";

        // When
        chatMemory.add(chatId1, List.of(new UserMessage("对话1的消息")));
        chatMemory.add(chatId2, List.of(new AssistantMessage("对话2的消息")));

        // Then
        List<Message> result1 = chatMemory.get(chatId1);
        List<Message> result2 = chatMemory.get(chatId2);

        assertEquals(1, result1.size());
        assertEquals(1, result2.size());
        assertEquals("对话1的消息", ((UserMessage) result1.get(0)).getText());
        assertEquals("对话2的消息", ((AssistantMessage) result2.get(0)).getText());
    }

    @Test
    void shouldPersistMessagesAcrossInstance() throws IOException {
        // Given
        String chatId = "chat-005";
        chatMemory.add(chatId, List.of(new UserMessage("持久化测试")));

        // When - 创建新实例读取同一目录
        FileBasedChatMemory anotherInstance = new FileBasedChatMemory(tempDir.toString());
        List<Message> result = anotherInstance.get(chatId);

        // Then - 文件持久化生效
        assertFalse(result.isEmpty());
        assertEquals("持久化测试", ((UserMessage) result.get(0)).getText());
    }
}
