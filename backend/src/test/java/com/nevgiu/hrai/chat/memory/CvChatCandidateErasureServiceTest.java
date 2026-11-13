package com.nevgiu.hrai.chat.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvChatCandidateErasureServiceTest {
    private final CvChatConversationCandidateRepository scopeCandidates =
            mock(CvChatConversationCandidateRepository.class);
    private final CvChatConversationRepository conversations = mock(CvChatConversationRepository.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final CvChatCandidateErasureService service =
            new CvChatCandidateErasureService(scopeCandidates, conversations, memory);

    @Test
    void clearsModelMemoryBeforeDeletingEveryConversationThatContainsTheCandidate() {
        when(scopeCandidates.findConversationIdsByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of("conversation-1", "conversation-2"));

        service.eraseCandidateConversations(7L, "tenant-a");

        var order = inOrder(memory, conversations);
        order.verify(memory).clear("conversation-1");
        order.verify(memory).clear("conversation-2");
        order.verify(conversations).deleteAllById(List.of("conversation-1", "conversation-2"));
        order.verify(conversations).flush();
    }
}
