package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvChatToolScopeTest {
    private final CvChatToolScope scope = new CvChatToolScope();

    @Test
    void acceptsOnlyCandidatesInTheImmutableHumanConfirmedScope() {
        scope.confirm(List.of(7L, 9L));

        assertThatCode(() -> scope.requireConfirmed(7L)).doesNotThrowAnyException();
        assertThatCode(() -> scope.confirm(List.of(9L, 7L))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireConfirmed(8L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate is outside the confirmed chat scope");
        assertThatThrownBy(() -> scope.confirm(List.of(7L, 8L)))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Confirmed candidate scope cannot change during a request");
    }

    @Test
    void rejectsInvalidConfirmedScopes() {
        assertThatThrownBy(() -> scope.confirm(null))
                .isInstanceOf(CvChatException.class);
        assertThatThrownBy(() -> scope.confirm(List.of()))
                .isInstanceOf(CvChatException.class);
        assertThatThrownBy(() -> scope.confirm(List.of(7L, 7L)))
                .isInstanceOf(CvChatException.class);
        assertThatThrownBy(() -> scope.confirm(java.util.Arrays.asList(7L, null)))
                .isInstanceOf(CvChatException.class);
        assertThatThrownBy(() -> scope.confirm(List.of(1L, 2L, 3L, 4L, 5L, 6L)))
                .isInstanceOf(CvChatException.class);
    }

    @Test
    void acceptsOnlyTheImmutableHumanConfirmedJob() {
        scope.confirmJob(21L);

        assertThatCode(() -> scope.requireConfirmedJob(21L)).doesNotThrowAnyException();
        assertThatCode(() -> scope.confirmJob(21L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireConfirmedJob(22L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Approved job is outside the confirmed chat scope");
        assertThatThrownBy(() -> scope.confirmJob(22L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Confirmed job scope cannot change during a request");
        assertThatThrownBy(() -> new CvChatToolScope().confirmJob(null))
                .isInstanceOf(CvChatException.class);
        assertThatThrownBy(() -> new CvChatToolScope().confirmJob(0L))
                .isInstanceOf(CvChatException.class);
    }
}
