package com.nevgiu.hrai.chat;

/**
 * Converts an already policy-approved recruiter question into a compact semantic-search query.
 *
 * <p>The normalized query is retrieval input only. It never replaces the recruiter's original question when the
 * answer model is called, and it never controls tenant or candidate scope.</p>
 */
interface CvChatRetrievalQueryNormalizer {

    String normalize(String question);
}
