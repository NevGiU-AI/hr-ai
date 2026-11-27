package com.nevgiu.hrai.bdd.steps;

import com.microsoft.playwright.Browser;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import com.nevgiu.hrai.bdd.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

public class NamedDiscoverySteps {
    private final ScenarioContext context;
    private String request;
    private List<String> selectedNames;

    public NamedDiscoverySteps(ScenarioContext context) {
        this.context = context;
    }

    @Given("a recruiter opens CV chat for named discovery")
    public void open() {
        request = System.getenv("BDD_NAMED_DISCOVERY_QUESTION");
        if (request == null || request.isBlank()) {
            throw new IllegalStateException("BDD_NAMED_DISCOVERY_QUESTION is required for this scenario");
        }
        var browserContext = context.browser().newContext(new Browser.NewContextOptions().setLocale("en-US"));
        context.recruiterBrowserContext(browserContext);
        var page = browserContext.newPage();
        context.recruiterPage(page);
        new LoginPage(page, context.configuration().baseUrl()).signIn(context.configuration().recruiter());
        chat().open();
    }

    @When("the recruiter submits the configured ambiguous name request")
    public void discover() {
        chat().discoverNamedCandidates(request);
    }

    @Then("the matches appear under two requested names without any selected candidates")
    public void assertGrouped() {
        chat().assertUnconfirmedNamedGroups();
    }

    @When("the recruiter selects one card from each name group and confirms scope")
    public void confirm() {
        var page = chat();
        selectedNames = page.selectOneFromEachNamedGroup();
        context.conversationId(page.startConversation());
    }

    @Then("only the two selected candidate cards remain in the active scope after reload")
    public void assertPersistedScope() {
        chat().assertNamedScopeAfterReload(selectedNames, context.conversationId());
    }

    private CvChatPage chat() {
        return new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
    }
}
