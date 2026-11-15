package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatConversationServiceTest {
    private final CvChatConversationRepository conversations = mock(CvChatConversationRepository.class);
    private final CvChatConversationCandidateRepository scopeCandidates =
            mock(CvChatConversationCandidateRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final CvChatMessageRepository messages = mock(CvChatMessageRepository.class);
    private final CvChatMessageCitationRepository messageCitations = mock(CvChatMessageCitationRepository.class);
    private final CvChatConversationService service = new CvChatConversationService(
            conversations, scopeCandidates, candidates, jobs, memory, messages, messageCitations,
            new CvChatConversationProperties(Duration.ofDays(7), 10, 100));

    @Test
    void createsOnlyFromTenantAuthorizedCandidates() {
        Candidate candidate = candidate(7L, "tenant-a", "Ada");
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CvChatConversationResponse response = service.create(
                new CvChatConversationRequests.Create(List.of(7L), null), actor(1L, "tenant-a"));

        assertThat(response.candidates()).containsExactly(
                new CvChatConversationResponse.CandidateCard(7L, "Ada", "Engineer"));
        assertThat(response.expiresAt()).isAfter(response.createdAt());
        verify(scopeCandidates).save(any(CvChatConversationCandidate.class));
    }

    @Test
    void hidesCrossTenantCandidateAsNotFound() {
        when(candidates.findByIdAndOrganizationId(9L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CvChatConversationRequests.Create(List.of(9L), null), actor(1L, "tenant-a")))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found");
        verify(conversations, never()).save(any());
    }

    @Test
    void rejectsDuplicateCandidateScope() {
        assertThatThrownBy(() -> service.create(
                new CvChatConversationRequests.Create(List.of(7L, 7L), null), actor(1L, "tenant-a")))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate IDs must be distinct");
    }

    @Test
    void hidesAnotherUsersConversation() {
        when(conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("conversation"), org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(2L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("conversation", actor(2L, "tenant-a")))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void listsOnlyTheAuthenticatedOwnersActiveConversations() {
        when(conversations.findAllByOrganizationIdAndOwnerUserIdAndExpiresAtAfterOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq(1L),
                any(), any())).thenReturn(List.of());

        assertThat(service.list(actor(1L, "tenant-a"))).isEmpty();

        verify(conversations).findAllByOrganizationIdAndOwnerUserIdAndExpiresAtAfterOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq(1L),
                any(), any());
    }

    @Test
    void returnsOnlyBoundedUserAndAssistantHistoryForTheOwner() {
        CvChatConversation conversation = new CvChatConversation(
                "conversation", "tenant-a", 1L, null, Instant.now(), Instant.now().plusSeconds(60));
        when(conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("conversation"), org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(Optional.of(conversation));
        when(memory.get("conversation")).thenReturn(List.of(
                new SystemMessage("internal"), new UserMessage("Which Java versions?"),
                new AssistantMessage("Java 17 [CV-7].")));

        CvChatConversationHistoryResponse response = service.history(
                "conversation", actor(1L, "tenant-a"));

        assertThat(response.messages()).containsExactly(
                new CvChatConversationHistoryResponse.TimelineMessage("USER", "Which Java versions?", List.of()),
                new CvChatConversationHistoryResponse.TimelineMessage(
                        "ASSISTANT", "Java 17 [CV-7].", List.of()));
    }

    @Test
    void restoresPersistedCitationNavigationWithoutDuplicatingCvExcerpts() {
        CvChatConversation conversation = new CvChatConversation(
                "conversation", "tenant-a", 1L, null, Instant.now(), Instant.now().plusSeconds(60));
        CvChatMessage assistant = mock(CvChatMessage.class);
        when(assistant.getId()).thenReturn(10L);
        when(assistant.getRole()).thenReturn("ASSISTANT");
        when(assistant.getContent()).thenReturn("Java 17 [CV-7].");
        when(conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("conversation"), org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(Optional.of(conversation));
        when(messages.findAllByConversationIdOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq("conversation"), any())).thenReturn(List.of(assistant));
        when(messageCitations.findAllByMessageIdOrderByDisplayOrder(10L)).thenReturn(List.of(
                new CvChatMessageCitation(10L, 0,
                        new com.nevgiu.hrai.chat.CvChatResponse.Citation("CV-7", 7L, 7L, null, "secret excerpt"))));

        CvChatConversationHistoryResponse response = service.history("conversation", actor(1L, "tenant-a"));

        assertThat(response.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo("ASSISTANT");
            assertThat(message.citations()).singleElement().satisfies(citation -> {
                assertThat(citation.reference()).isEqualTo("CV-7");
                assertThat(citation.excerpt()).isEmpty();
            });
        });
        verifyNoInteractions(memory);
    }

    @Test
    void replacingVisibleScopeClearsHistoryFromThePreviousScope() {
        CvChatConversation conversation = new CvChatConversation(
                "conversation", "tenant-a", 1L, null, Instant.now(), Instant.now().plusSeconds(60));
        Candidate candidate = candidate(8L, "tenant-a", "Grace");
        when(conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("conversation"), org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(Optional.of(conversation));
        when(candidates.findByIdAndOrganizationId(8L, "tenant-a")).thenReturn(Optional.of(candidate));

        service.replaceScope("conversation",
                new CvChatConversationRequests.ReplaceScope(0, List.of(8L), null), actor(1L, "tenant-a"));

        verify(memory).clear("conversation");
        verify(messages).deleteAllByConversationId("conversation");
        verify(scopeCandidates).deleteAllByIdConversationId("conversation");
        verify(scopeCandidates).save(any(CvChatConversationCandidate.class));
    }

    @Test
    void deletionClearsSpringMemoryBeforeRemovingScope() {
        CvChatConversation conversation = new CvChatConversation(
                "conversation", "tenant-a", 1L, null, Instant.now(), Instant.now().plusSeconds(60));
        when(conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                org.mockito.ArgumentMatchers.eq("conversation"), org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(Optional.of(conversation));

        service.delete("conversation", actor(1L, "tenant-a"));

        verify(memory).clear("conversation");
        verify(conversations).delete(conversation);
    }

    @Test
    void refusesToPersistATurnWhenTheConfirmedScopeVersionChangedDuringProviderWork() {
        CvChatConversation conversation = new CvChatConversation(
                "conversation", "tenant-a", 1L, null, Instant.now(), Instant.now().plusSeconds(60));
        when(conversations.findLockedById("conversation")).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.appendTurn("conversation", 1,
                "Follow-up", "Generated answer", List.of(), actor(1L, "tenant-a")))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Conversation scope changed; retry the question");

        verifyNoInteractions(memory);
        verifyNoInteractions(messages, messageCitations);
        verify(conversations, never()).flush();
    }

    private Candidate candidate(Long id, String organizationId, String name) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setOrganizationId(organizationId);
        candidate.setName(name);
        candidate.setCurrentTitle("Engineer");
        return candidate;
    }

    private AppUserPrincipal actor(Long id, String organizationId) {
        return new AppUserPrincipal(id, "user@example.com", "hash", organizationId, true,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));
    }
}
