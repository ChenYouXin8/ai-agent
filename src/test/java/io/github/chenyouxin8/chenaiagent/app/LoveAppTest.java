package io.github.chenyouxin8.chenaiagent.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoveAppTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatOptions chatOptions;

    private LoveApp loveApp;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(chatOptions);
        when(chatOptions.mutate()).thenReturn(mock(ChatOptions.Builder.class));

        loveApp = new LoveApp(chatModel);
    }

    @Test
    void doChat_ShouldReturnResponseContent() {
        // Given
        String message = "我喜欢一个女生，但不知道如何表白";
        String chatId = "test-chat-001";
        String expectedContent = "勇敢去表达你的心意吧！先从朋友做起，逐步建立好感。";

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(expectedContent);

        // When
        String result = loveApp.doChat(message, chatId);

        // Then
        assertNotNull(result);
        assertEquals(expectedContent, result);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void doChat_ShouldHandleDifferentChatIds() {
        // Given
        String message = "我们经常吵架怎么办";
        String chatId1 = "user-001";
        String chatId2 = "user-002";

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("尝试换位思考，多沟通。");

        // When
        String result1 = loveApp.doChat(message, chatId1);
        String result2 = loveApp.doChat(message, chatId2);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1, result2);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
