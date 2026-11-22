package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvChatToolArgumentsTest {

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void rejectsMissingShortAndOversizedQueriesWithoutEchoingThem(String query) {
        assertThatThrownBy(() -> CvChatToolArguments.requireBoundedQuery(query))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool query must contain between 2 and 2000 characters");
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsMissingAndNonPositiveIds(Long id) {
        assertThatThrownBy(() -> CvChatToolArguments.requirePositiveId(id))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool ID must be a positive number");
    }

    @ParameterizedTest
    @MethodSource("normalizedQueries")
    void normalizesBoundedQueriesBeforeDownstreamWork(String query, String expected) {
        assertThat(CvChatToolArguments.requireBoundedQuery(query)).isEqualTo(expected);
    }

    private static Stream<String> invalidQueries() {
        return Stream.of(null, "", " ", "A", "x".repeat(2_001));
    }

    private static Stream<Long> invalidIds() {
        return Stream.of(null, 0L, -1L, Long.MIN_VALUE);
    }

    private static Stream<Arguments> normalizedQueries() {
        return Stream.of(
                Arguments.of("  Ada   Lovelace  ", "Ada Lovelace"),
                Arguments.of("Java\nSpring", "Java Spring"),
                Arguments.of("x".repeat(2_000), "x".repeat(2_000)));
    }
}
