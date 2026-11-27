package com.nevgiu.hrai.bdd.steps;

import com.microsoft.playwright.Browser;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import com.nevgiu.hrai.bdd.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

public class OrganizationDiscoverySteps {
    private final ScenarioContext context;
    private List<String> confirmedIds;

    public OrganizationDiscoverySteps(ScenarioContext context) {
        this.context = context;
    }

    @Given("a recruiter is ready to discover candidates in CV chat")
    public void signIn() {
        // Validate fixture configuration before opening a session or submitting a paid request.
        required("BDD_ORGANIZATION_DISCOVERY_QUESTION");
        required("BDD_ORGANIZATION_COMPARISON_QUESTION");
        var browserContext = context.browser().newContext(new Browser.NewContextOptions().setLocale("en-US"));
        context.recruiterBrowserContext(browserContext);
        var page = browserContext.newPage();
        context.recruiterPage(page);
        new LoginPage(page, context.configuration().baseUrl()).signIn(context.configuration().recruiter());
        chat().open();
    }

    @When("the recruiter discovers candidates using the configured organization question")
    public void discover() {
        chat().discoverOrganizationCandidates(required("BDD_ORGANIZATION_DISCOVERY_QUESTION"));
    }

    @Then("the shortlist awaits explicit confirmation")
    public void assertUnconfirmed() {
        chat().assertOrganizationShortlistUnconfirmed();
    }

    @When("the recruiter confirms two of the proposed candidates")
    public void confirmSubset() {
        CvChatPage page = chat();
        confirmedIds = page.selectFirstTwoOrganizationMatches();
        context.conversationId(page.startConversation());
        page.assertConfirmedScopeSize(2);
    }

    @When("the recruiter asks the configured comparison question")
    public void compare() {
        String endpoint = context.configuration().apiUrl() + "/chat/conversations/"
                + context.conversationId() + "/messages";
        chat().askComparison(required("BDD_ORGANIZATION_COMPARISON_QUESTION"), endpoint);
    }

    @Then("the comparison cites both confirmed candidates and no others")
    public void verifyCitations() {
        chat().assertLatestCitationCandidateIds(confirmedIds);
    }

    private CvChatPage chat() {
        return new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for this scenario");
        return value;
    }
}
