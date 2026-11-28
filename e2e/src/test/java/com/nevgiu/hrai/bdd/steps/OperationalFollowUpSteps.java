package com.nevgiu.hrai.bdd.steps;

import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.regex.Pattern;

/** Exercises explicit operational routes with the same disposable conversation. */
public class OperationalFollowUpSteps {
    private final ScenarioContext context;

    public OperationalFollowUpSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the recruiter asks about extraction, ingestion warnings, and stored evaluation scores")
    public void askOperationalQuestions() {
        CvChatPage chat = chat();
        chat.askOperationalQuestion("Was this CV processed with OCR?",
                Pattern.compile("Current document status is .*Text extraction used (?:NATIVE|OCR)\\."));
        // Either a reviewed warning category or the explicit empty result is valid.
        chat.askOperationalQuestion("Were ingestion warnings recorded?",
                Pattern.compile("Ingestion warnings: [A-Z_]+|No ingestion warnings are recorded\\."));
        // Require real stored history; an empty fixture would not exercise rendering evaluation scores.
        chat.askOperationalQuestion("What overall fit, skills match, and AI confidence scores were stored in the latest evaluation?",
                Pattern.compile("Persisted AI-assisted evaluation history: Evaluation \\d+ for .* is (?:CURRENT|STALE),"
                        + ".*overall fit \\d+/100, skills match \\d+/100,.*AI confidence \\d+/100\\."
                        + ".*Scores are decision-support context for human review"));
    }

    @Then("the operational replies stay in the same confirmed conversation without CV citations")
    public void verifyOperationalReplies() {
        chat().assertOperationalHistory(context.configuration().candidateName(), context.conversationId());
    }

    private CvChatPage chat() {
        return new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
    }
}
