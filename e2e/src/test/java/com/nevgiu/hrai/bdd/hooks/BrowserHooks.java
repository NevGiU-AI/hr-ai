package com.nevgiu.hrai.bdd.hooks;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/** Owns browser resources and cleanup for exactly one Cucumber scenario. */
public class BrowserHooks {
    private final ScenarioContext context;

    public BrowserHooks(ScenarioContext context) {
        this.context = context;
    }

    @Before
    public void launchBrowser() {
        Playwright playwright = Playwright.create();
        context.playwright(playwright);
        context.browser(playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(context.configuration().headless())));
    }

    @After
    public void closeBrowser(Scenario scenario) {
        try {
            if (scenario.isFailed() && context.recruiterPage() != null
                    && !context.recruiterPage().isClosed()) {
                scenario.attach(context.recruiterPage().screenshot(), "image/png", "browser-failure");
            }
            deleteDisposableConversation();
        } finally {
            closeQuietly(context.administratorBrowserContext());
            closeQuietly(context.recruiterBrowserContext());
            if (context.browser() != null) context.browser().close();
            if (context.playwright() != null) context.playwright().close();
        }
    }

    private void deleteDisposableConversation() {
        if (context.conversationId() == null || context.recruiterPage() == null
                || context.recruiterPage().isClosed()) {
            return;
        }
        new CvChatPage(context.recruiterPage(), context.configuration().baseUrl())
                .deleteActiveConversation();
        context.conversationId(null);
    }

    private static void closeQuietly(com.microsoft.playwright.BrowserContext browserContext) {
        if (browserContext != null) browserContext.close();
    }
}
