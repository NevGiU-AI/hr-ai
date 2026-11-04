package com.nevgiu.hrai.chat.tool;

/**
 * Minimized approved-job criteria exposed to the model after explicit human confirmation.
 *
 * <p>Compensation, benefits, tone, organization identifiers, and generation metadata are deliberately absent. Long
 * free-text fields are bounded by {@link CvChatReadTools} before this value crosses the tool boundary.</p>
 */
public record JobRequirements(
        Long id,
        String title,
        String level,
        String summary,
        String responsibilities,
        String requiredQualifications,
        String preferredQualifications,
        String softSkills
) { }
