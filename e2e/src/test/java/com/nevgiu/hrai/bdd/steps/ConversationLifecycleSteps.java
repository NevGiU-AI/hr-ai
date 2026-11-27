package com.nevgiu.hrai.bdd.steps;

import com.microsoft.playwright.APIResponse;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/** Browser-level acceptance steps for persisted conversation lifecycle behavior. */
public class ConversationLifecycleSteps {
    private final ScenarioContext context;
    private String editedJobTitle;

    public ConversationLifecycleSteps(ScenarioContext context) {
        this.context = context;
    }

    @When("the recruiter leaves and resumes that conversation")
    public void recruiterLeavesAndResumesConversation() {
        CvChatPage chat = chat();
        chat.leaveActiveConversation();
        chat.resumeConversation(context.configuration().candidateName(), context.conversationId());
    }

    @Then("the same confirmed candidate scope survives a browser reload")
    public void candidateScopeSurvivesReload() {
        chat().reloadActiveConversation(context.configuration().candidateName(), context.conversationId());
    }

    @When("the recruiter asks for the CV extraction status")
    public void recruiterAsksForExtractionStatus() {
        chat().askForExtractionStatus();
    }

    @When("the recruiter adds an approved job to the confirmed scope")
    public void recruiterAddsApprovedJob() {
        editedJobTitle = chat().addApprovedJob();
    }

    @Then("the same conversation keeps the edited scope and an empty timeline after reload")
    public void editedScopeSurvivesReload() {
        CvChatPage chat = chat();
        chat.assertEditedScope(context.configuration().candidateName(), context.conversationId(), editedJobTitle);
        chat.reloadActiveConversation(context.configuration().candidateName(), context.conversationId());
        chat.assertEditedScope(context.configuration().candidateName(), context.conversationId(), editedJobTitle);
    }

    @When("the recruiter asks the configured grounded question")
    public void recruiterAsksConfiguredQuestion() {
        chat().askGroundedQuestion(context.configuration().candidateQuestion());
    }

    @Then("the first citation opens its rendered CV document")
    public void citationOpensRenderedDocument() {
        chat().openFirstCitationAndVerifyTarget();
    }

    @When("the recruiter deletes that conversation")
    public void recruiterDeletesConversation() {
        // This is not a shared-fixture destructive test. The identifier was created by this scenario, and clearing it
        // here also prevents the After hook from attempting to delete an already-deleted disposable conversation.
        context.deletedConversationId(context.conversationId());
        chat().deleteActiveConversation();
        context.conversationId(null);
    }

    @Then("the deleted conversation is cleared from the active browser state")
    public void deletedConversationIsClearedFromBrowserState() {
        chat().assertNoActiveConversationPointer();
    }

    @Then("the recruiter can no longer retrieve the deleted conversation")
    public void recruiterCannotRetrieveDeletedConversation() {
        String endpoint = context.configuration().apiUrl() + "/chat/conversations/"
                + context.deletedConversationId();
        APIResponse response = context.recruiterBrowserContext().request().get(endpoint);
        assertThat(response.status()).isEqualTo(404);
    }

    private CvChatPage chat() {
        return new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
    }
}
