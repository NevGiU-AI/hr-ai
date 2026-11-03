package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatLanguageScopeServiceTest {
    private final CvChatCandidateReferenceExtractor extractor = mock(CvChatCandidateReferenceExtractor.class);
    private final CvChatCandidateResolutionService resolution = mock(CvChatCandidateResolutionService.class);
    private final CvChatLanguageScopeService service = new CvChatLanguageScopeService(extractor, resolution);

    @Test
    void resolvesExactAndPartialReferencesForExplicitConfirmation() {
        when(extractor.extract("Compare Ada Lovelace and Grace")).thenReturn(List.of("Ada Lovelace", "Grace"));
        when(resolution.resolve("Ada Lovelace", "tenant-a")).thenReturn(new CvChatCandidateResolutionResponse(
                CvChatCandidateResolutionResponse.Status.RESOLVED,
                List.of(new CvChatCandidateResolutionResponse.CandidateOption(7L, "Ada Lovelace", "Engineer")),
                false));
        when(resolution.resolve("Grace", "tenant-a"))
                .thenThrow(new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
        when(resolution.search("Grace", "tenant-a")).thenReturn(new CvChatCandidateSearchResponse(List.of(
                new CvChatCandidateSearchResponse.CandidateOption(8L, "Grace Hopper", "Architect"),
                new CvChatCandidateSearchResponse.CandidateOption(9L, "Grace Murray", "Developer")), false));

        CvChatLanguageScopeResponse response = service.propose(" Compare Ada Lovelace and Grace ", "tenant-a");

        assertThat(response.message()).isEqualTo("Compare Ada Lovelace and Grace");
        assertThat(response.references()).extracting(CvChatLanguageScopeResponse.Reference::status)
                .containsExactly(CvChatLanguageScopeResponse.Status.RESOLVED,
                        CvChatLanguageScopeResponse.Status.CHOICES);
        assertThat(response.references().get(1).candidates()).extracting(
                CvChatLanguageScopeResponse.CandidateOption::id).containsExactly(8L, 9L);
    }

    @Test
    void returnsNoCandidateCardsWhenMessageDoesNotContainTwoReferences() {
        when(extractor.extract("Who has Java experience?")).thenReturn(List.of());

        CvChatLanguageScopeResponse response = service.propose("Who has Java experience?", "tenant-a");

        assertThat(response.references()).isEmpty();
        verifyNoInteractions(resolution);
    }
}
