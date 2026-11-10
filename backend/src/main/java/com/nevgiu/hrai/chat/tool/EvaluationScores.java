package com.nevgiu.hrai.chat.tool;

/**
 * Persisted evaluation scores exposed through the bounded read tool.
 *
 * <p>Skills match, keyword density, AI confidence, and overall fit use 0–100. Experience, education, achievement,
 * employment gap, and readability use 0–10; an employment-gap value of 10 means no gap was detected. These are stored
 * model outputs for human review, not autonomous hiring decisions.</p>
 */
public record EvaluationScores(
        int skillsMatch,
        int experienceRelevance,
        int educationFit,
        int achievementImpact,
        int keywordDensity,
        int employmentGap,
        int readability,
        int aiConfidence,
        int overallFit
) { }
