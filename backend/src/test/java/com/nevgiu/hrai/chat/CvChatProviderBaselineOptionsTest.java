package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvChatProviderBaselineOptionsTest {
    @Test
    void keepsProviderOptionsAndRecordsUsageThroughTheRealChatClientWithoutNetworkCalls() {
        var provider = mock(OpenAiChatModel.class);
        when(provider.getOptions()).thenReturn(OpenAiChatOptions.builder().model("baseline-test-model").build());
        var expectedUsage = mock(Usage.class);
        when(provider.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
                assertThat(options.getModel()).isEqualTo("baseline-test-model");
                assertThat(options.getTemperature()).isZero();
            });
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Java 17 [CV-61]."))),
                    ChatResponseMetadata.builder().usage(expectedUsage).build());
        });

        var usage = new AtomicReference<Usage>();
        var chat = new SpringAiCvChatModel(ChatClient.builder(CvChatProviderBaselineTest.measure(provider, usage))
                .build());
        var evidence = List.of(new CvChatEvidence("CV-61", 60L, 61L, null, "Java 17 experience."));

        assertThat(chat.answer("Which Java version?", evidence)).isEqualTo("Java 17 [CV-61].");
        assertThat(usage.get()).isSameAs(expectedUsage);
    }
}
