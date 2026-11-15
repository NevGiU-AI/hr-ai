package com.nevgiu.hrai.bdd.steps;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import com.nevgiu.hrai.bdd.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Step definitions for the conversation-owner authorization boundary. */
public class ConversationOwnershipSteps {
    private final ScenarioContext context;

    public ConversationOwnershipSteps(ScenarioContext context) {
        this.context = context;
    }

    @Given("a recruiter owns an active CV-chat conversation")
    public void recruiterOwnsConversation() {
        BrowserContext recruiter = context.browser().newContext();
        context.recruiterBrowserContext(recruiter);
        Page page = recruiter.newPage();
        context.recruiterPage(page);
        new LoginPage(page, context.configuration().baseUrl()).signIn(context.configuration().recruiter());
        CvChatPage chat = new CvChatPage(page, context.configuration().baseUrl());
        chat.open();
        chat.selectCandidate(context.configuration().candidateQuery(), context.configuration().candidateName());
        context.conversationId(chat.startConversation());
    }

    @Given("an administrator belongs to the same organization")
    public void administratorBelongsToSameOrganization() {
        BrowserContext administrator = context.browser().newContext();
        context.administratorBrowserContext(administrator);
        Page page = administrator.newPage();
        context.administratorPage(page);
        new LoginPage(page, context.configuration().baseUrl()).signIn(context.configuration().administrator());
    }

    @When("the administrator requests the recruiter's conversation")
    public void administratorRequestsConversation() {
        String endpoint = context.configuration().apiUrl() + "/chat/conversations/" + context.conversationId();
        APIResponse response = context.administratorBrowserContext().request().get(endpoint);
        context.lastApiResponse(response);
        byte[] body = response.body();
        context.lastApiResponseBody(body.length == 0 ? "" : new String(body, StandardCharsets.UTF_8));
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expectedStatus) {
        assertThat(context.lastApiResponse()).as("administrator conversation response").isNotNull();
        assertThat(context.lastApiResponse().status()).isEqualTo(expectedStatus);
    }

    @Then("no conversation metadata is disclosed")
    public void noConversationMetadataIsDisclosed() {
        // Spring Security or the application exception handler may produce the opaque 404 response. The optional
        // human-readable message therefore is not part of this security contract; status/error and non-disclosure are.
        assertThat(context.lastApiResponseBody())
                .contains("\"status\":404")
                .contains("\"error\":\"Not Found\"")
                .doesNotContain("\"candidates\"")
                .doesNotContain("\"job\"")
                .doesNotContain("\"version\"")
                .doesNotContain("\"expiresAt\"");
    }
}
