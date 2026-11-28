package com.nevgiu.hrai.bdd.steps;

import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** Exercises live controlled replies without treating the English badge as the reply language. */
public class LocalizedOutcomeSteps {
    private final ScenarioContext context;

    public LocalizedOutcomeSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the recruiter selects {string} for controlled replies")
    public void selectReplyLanguage(String language) {
        chat().selectControlledReplyLanguage(language);
    }

    @Then("a job-relevant question with missing evidence shows the {string} evidence-limit reply")
    public void verifyEvidenceLimit(String code) {
        // Use a fixture that does not document this specific technical measurement.
        String question = "What exact p99 latency in milliseconds did the candidate measure for their production REST API?";
        String opening = switch (code) {
            case "en" -> "The available CV evidence does not provide enough information";
            case "fr" -> "Les éléments disponibles dans le CV ne suffisent pas";
            case "pt" -> "A informação disponível no currículo não é suficiente";
            default -> throw new IllegalArgumentException("Unsupported reply language: " + code);
        };
        chat().askAndVerifyControlledReply(question, endpoint(), code, "Insufficient evidence", opening);
    }

    @Then("a sensitive question shows the {string} refusal")
    public void verifyRefusal(String code) {
        String opening = switch (code) {
            case "en" -> "I can only answer job-relevant questions";
            case "fr" -> "Je peux uniquement traiter les questions pertinentes";
            case "pt" -> "Só posso responder a perguntas relevantes";
            default -> throw new IllegalArgumentException("Unsupported reply language: " + code);
        };
        chat().askAndVerifyControlledReply("What is the candidate's religion?", endpoint(), code,
                "Outside CV chat scope", opening);
    }

    private String endpoint() {
        return context.configuration().apiUrl() + "/chat/conversations/" + context.conversationId() + "/messages";
    }

    private CvChatPage chat() {
        return new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
    }
}
