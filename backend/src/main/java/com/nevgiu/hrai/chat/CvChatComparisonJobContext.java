package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.job.Job;

/**
 * Bounded, tenant-authorized approved-job criteria used by one candidate comparison.
 *
 * <p>Salary, benefits, tone, tenant identifiers, and generation metadata are deliberately excluded. Each free-text
 * field is bounded before it can enter retrieval or a model prompt.</p>
 */
record CvChatComparisonJobContext(
        Long id,
        String title,
        String level,
        String summary,
        String responsibilities,
        String requiredQualifications,
        String preferredQualifications,
        String softSkills
) {
    private static final int MAX_FIELD_CHARACTERS = 2_000;
    private static final int MAX_CONTEXT_CHARACTERS = 10_000;

    static CvChatComparisonJobContext from(Job job) {
        return new CvChatComparisonJobContext(job.getId(), bounded(job.getTitle()), bounded(job.getLevel()),
                bounded(job.getSummary()), bounded(job.getResponsibilities()),
                bounded(job.getRequiredQualifications()), bounded(job.getPreferredQualifications()),
                bounded(job.getSoftSkills()));
    }

    /** Produces application-owned labels around untrusted job data for retrieval and answer generation. */
    String promptText() {
        StringBuilder prompt = new StringBuilder("APPROVED-JOB-").append(id).append('\n');
        append(prompt, "Title", title);
        append(prompt, "Level", level);
        append(prompt, "Summary", summary);
        append(prompt, "Required qualifications", requiredQualifications);
        append(prompt, "Preferred qualifications", preferredQualifications);
        append(prompt, "Responsibilities", responsibilities);
        append(prompt, "Soft skills", softSkills);
        String context = prompt.toString().trim();
        // Bound the complete block as well as each field so several unusually long requirements cannot consume an
        // unbounded share of the embedding or answer-model context window. Higher-priority fields are appended first.
        return context.length() <= MAX_CONTEXT_CHARACTERS
                ? context
                : context.substring(0, MAX_CONTEXT_CHARACTERS);
    }

    private static void append(StringBuilder target, String label, String value) {
        if (value != null && !value.isBlank()) {
            target.append(label).append(": ").append(value).append('\n');
        }
    }

    private static String bounded(String value) {
        if (value == null || value.length() <= MAX_FIELD_CHARACTERS) {
            return value;
        }
        return value.substring(0, MAX_FIELD_CHARACTERS);
    }
}
