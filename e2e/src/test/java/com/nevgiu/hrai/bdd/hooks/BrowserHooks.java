package com.nevgiu.hrai.bdd.hooks;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.CvChatPage;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

import java.net.URI;

import static com.microsoft.playwright.options.AriaRole.BUTTON;

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
            // Closing a browser context discards its cookies locally but does not invalidate the server-side Spring
            // Session. Explicit logout keeps repeated local and CI runs from accumulating sessions for the dedicated
            // BDD accounts and prevents misleading SESSION_LIMIT_ENFORCED audit events. It remains best effort so an
            // unavailable logout endpoint cannot replace the scenario's real result during cleanup.
            logoutQuietly(context.administratorPage());
            logoutQuietly(context.otherTenantPage());
            logoutQuietly(context.recruiterPage());
            closeQuietly(context.administratorBrowserContext());
            closeQuietly(context.otherTenantBrowserContext());
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
        // Angular owns CSRF propagation for state-changing requests. Return from a citation's candidate-workspace
        // route to chat, let the persisted pointer restore the conversation, and delete it through the UI.
        CvChatPage chat = new CvChatPage(context.recruiterPage(), context.configuration().baseUrl());
        chat.open();
        chat.deleteActiveConversation();
        context.conversationId(null);
    }

    private static void logoutQuietly(Page page) {
        if (page == null || page.isClosed()) return;
        try {
            var signOut = page.getByRole(BUTTON,
                    new Page.GetByRoleOptions().setName("Sign out").setExact(true));
            if (!signOut.isVisible()) return;
            signOut.click();
            page.waitForURL(url -> URI.create(url).getPath().equals("/login"));
        } catch (PlaywrightException | IllegalArgumentException ignored) {
            // Cleanup is deliberately non-destructive and best effort. Context closure below still guarantees that
            // browser cookies and storage cannot leak into the next PicoContainer-scoped Cucumber scenario.
        }
    }

    private static void closeQuietly(com.microsoft.playwright.BrowserContext browserContext) {
        if (browserContext != null) browserContext.close();
    }
}
