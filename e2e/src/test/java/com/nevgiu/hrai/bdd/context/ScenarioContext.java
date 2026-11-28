package com.nevgiu.hrai.bdd.context;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.nevgiu.hrai.bdd.config.BddConfiguration;

/**
 * Scenario-scoped mutable state shared through PicoContainer.
 *
 * <p>Cucumber creates a new instance for every scenario. Keeping browser sessions and IDs here prevents cookies,
 * local storage, and conversation identifiers from leaking between scenarios.</p>
 */
public class ScenarioContext {
    private final BddConfiguration configuration = BddConfiguration.fromEnvironment();
    private Playwright playwright;
    private Browser browser;
    private BrowserContext recruiterBrowserContext;
    private BrowserContext administratorBrowserContext;
    private Page recruiterPage;
    private Page administratorPage;
    private BrowserContext otherTenantBrowserContext;
    private Page otherTenantPage;
    private String conversationId;
    private String deletedConversationId;
    private APIResponse lastApiResponse;
    private String lastApiResponseBody;

    public BddConfiguration configuration() { return configuration; }
    public Playwright playwright() { return playwright; }
    public void playwright(Playwright value) { playwright = value; }
    public Browser browser() { return browser; }
    public void browser(Browser value) { browser = value; }
    public BrowserContext recruiterBrowserContext() { return recruiterBrowserContext; }
    public void recruiterBrowserContext(BrowserContext value) { recruiterBrowserContext = value; }
    public BrowserContext administratorBrowserContext() { return administratorBrowserContext; }
    public void administratorBrowserContext(BrowserContext value) { administratorBrowserContext = value; }
    public Page recruiterPage() { return recruiterPage; }
    public void recruiterPage(Page value) { recruiterPage = value; }
    public Page administratorPage() { return administratorPage; }
    public void administratorPage(Page value) { administratorPage = value; }
    public BrowserContext otherTenantBrowserContext() { return otherTenantBrowserContext; }
    public void otherTenantBrowserContext(BrowserContext value) { otherTenantBrowserContext = value; }
    public Page otherTenantPage() { return otherTenantPage; }
    public void otherTenantPage(Page value) { otherTenantPage = value; }
    public String conversationId() { return conversationId; }
    public void conversationId(String value) { conversationId = value; }
    public String deletedConversationId() { return deletedConversationId; }
    public void deletedConversationId(String value) { deletedConversationId = value; }
    public APIResponse lastApiResponse() { return lastApiResponse; }
    public void lastApiResponse(APIResponse value) { lastApiResponse = value; }
    public String lastApiResponseBody() { return lastApiResponseBody; }
    public void lastApiResponseBody(String value) { lastApiResponseBody = value; }
}
