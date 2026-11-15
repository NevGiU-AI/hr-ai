package com.nevgiu.hrai.bdd.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Validated external configuration for one browser-test process. */
public record BddConfiguration(
        URI baseUrl,
        URI apiUrl,
        Credentials recruiter,
        Credentials administrator,
        String candidateQuery,
        String candidateName,
        String candidateQuestion,
        boolean headless) {

    public static BddConfiguration fromEnvironment() {
        List<String> missing = new ArrayList<>();
        String baseUrl = required("BDD_BASE_URL", missing);
        String apiUrl = required("BDD_API_URL", missing);
        String recruiterEmail = required("BDD_RECRUITER_EMAIL", missing);
        String recruiterPassword = required("BDD_RECRUITER_PASSWORD", missing);
        String adminEmail = required("BDD_ADMIN_EMAIL", missing);
        String adminPassword = required("BDD_ADMIN_PASSWORD", missing);
        String candidateQuery = required("BDD_CANDIDATE_QUERY", missing);
        String candidateName = required("BDD_CANDIDATE_NAME", missing);
        String candidateQuestion = required("BDD_CANDIDATE_QUESTION", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required BDD environment variables: "
                    + String.join(", ", missing));
        }
        return new BddConfiguration(
                normalizedUri("BDD_BASE_URL", baseUrl),
                normalizedUri("BDD_API_URL", apiUrl),
                new Credentials(recruiterEmail, recruiterPassword),
                new Credentials(adminEmail, adminPassword),
                candidateQuery,
                candidateName,
                candidateQuestion,
                !"false".equalsIgnoreCase(System.getenv().getOrDefault("BDD_HEADLESS", "true")));
    }

    private static String required(String name, List<String> missing) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
            return "";
        }
        return value.trim();
    }

    private static URI normalizedUri(String name, String value) {
        try {
            URI uri = URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be an absolute HTTP(S) URL", exception);
        }
    }

    public record Credentials(String email, String password) {
    }
}
