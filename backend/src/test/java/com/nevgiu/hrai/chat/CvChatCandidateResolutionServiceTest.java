package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.candidate.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvChatCandidateResolutionServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvChatCandidateResolutionService service = new CvChatCandidateResolutionService(candidates);

    @Test
    void resolvesOneExactTenantCandidateAndReturnsOnlyMinimalIdentityData() {
        CandidateIdentity candidate = candidate(7L, "Ada Lovelace", "Engineer");
        when(candidates.findAllByExactNameAndOrganizationId(
                "Ada Lovelace", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        CvChatCandidateResolutionResponse response = service.resolve("  Ada   Lovelace  ", "tenant-a");

        assertThat(response.status()).isEqualTo(CvChatCandidateResolutionResponse.Status.RESOLVED);
        assertThat(response.candidates()).containsExactly(
                new CvChatCandidateResolutionResponse.CandidateOption(7L, "Ada Lovelace", "Engineer"));
        assertThat(response.truncated()).isFalse();
        verify(candidates).findAllByExactNameAndOrganizationId(
                "Ada Lovelace", "tenant-a", PageRequest.of(0, 20));
    }

    @Test
    void requiresExplicitSelectionWhenAnExactNameIsAmbiguous() {
        when(candidates.findAllByExactNameAndOrganizationId(
                "Alex Smith", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(candidate(9L, "Alex Smith", "Platform Engineer"),
                        candidate(4L, "Alex Smith", "Data Engineer"))));

        CvChatCandidateResolutionResponse response = service.resolve("Alex Smith", "tenant-a");

        assertThat(response.status()).isEqualTo(CvChatCandidateResolutionResponse.Status.AMBIGUOUS);
        assertThat(response.candidates()).extracting(CvChatCandidateResolutionResponse.CandidateOption::id)
                .containsExactly(9L, 4L);
    }

    @Test
    void reportsWhenTheBoundedAmbiguityListHasMoreMatches() {
        List<CandidateIdentity> options = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> candidate(id, "Alex Smith", "Engineer"))
                .toList();
        when(candidates.findAllByExactNameAndOrganizationId(
                "Alex Smith", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(options, PageRequest.of(0, 20), true));

        CvChatCandidateResolutionResponse response = service.resolve("Alex Smith", "tenant-a");

        assertThat(response.status()).isEqualTo(CvChatCandidateResolutionResponse.Status.AMBIGUOUS);
        assertThat(response.candidates()).hasSize(20);
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void returnsTheSameGenericNotFoundResultForMissingOrCrossTenantNames() {
        when(candidates.findAllByExactNameAndOrganizationId(
                "Grace Hopper", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> service.resolve("Grace Hopper", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .satisfies(error -> {
                    CvChatException exception = (CvChatException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getMessage()).isEqualTo("Candidate not found");
                });
    }

    @Test
    void returnsBoundedPartialMatchesForExplicitSelection() {
        when(candidates.searchByNameAndOrganizationId(
                "%fanon%", "fanon", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(
                        candidate(3L, "Fanon", "Engineer"),
                        candidate(7L, "Fanon Jupkwo", "Java Engineer")),
                        PageRequest.of(0, 20), true));

        CvChatCandidateSearchResponse response = service.search("  Fanon  ", "tenant-a");

        assertThat(response.candidates()).extracting(CvChatCandidateSearchResponse.CandidateOption::id)
                .containsExactly(3L, 7L);
        assertThat(response.truncated()).isTrue();
        verify(candidates).searchByNameAndOrganizationId(
                "%fanon%", "fanon", "tenant-a", PageRequest.of(0, 20));
    }

    @Test
    void treatsLikeControlCharactersAsLiteralCandidateNameText() {
        when(candidates.searchByNameAndOrganizationId(
                "%a!%!_!!%", "a%_!", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of()));

        CvChatCandidateSearchResponse response = service.search("A%_!", "tenant-a");

        assertThat(response.candidates()).isEmpty();
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void matchesMultiTokenNamesRegardlessOfStoredTokenOrder() {
        when(candidates.searchByNameAndOrganizationId(
                "%boris%", "boris zok", "tenant-a", PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of(
                        candidate(52L, "Resume ZOK Boris", "AI / ML Engineer"),
                        candidate(53L, "Boris Other", "Engineer"))));

        CvChatCandidateSearchResponse response = service.search("Boris Zok", "tenant-a");

        assertThat(response.candidates()).extracting(CvChatCandidateSearchResponse.CandidateOption::id)
                .containsExactly(52L);
        verify(candidates).searchByNameAndOrganizationId(
                "%boris%", "boris zok", "tenant-a", PageRequest.of(0, 20));
    }

    @Test
    void rejectsPartialQueriesShorterThanTwoCharacters() {
        assertThatThrownBy(() -> service.search(" A ", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .satisfies(error -> assertThat(((CvChatException) error).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private CandidateIdentity candidate(Long id, String name, String currentTitle) {
        return new CandidateIdentity(id, name, currentTitle);
    }
}
