package com.nevgiu.hrai.bdd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.net.URI;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.options.AriaRole.BUTTON;
import static com.microsoft.playwright.options.AriaRole.HEADING;

/** Browser actions for selecting a candidate and creating or deleting a conversation. */
public class CvChatPage {
    private static final String ACTIVE_CONVERSATION_KEY = "hr-ai-active-cv-chat-conversation";

    private final Page page;
    private final URI baseUrl;

    public CvChatPage(Page page, URI baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public void open() {
        page.navigate(baseUrl + "/chat");
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("CV chat").setExact(true))).isVisible();
    }

    public void selectCandidate(String query, String exactCandidateName) {
        // Candidate search is now an optional fallback inside a collapsed <details> element because natural-language
        // discovery is the primary UI. BDD deliberately exercises picker-first confirmation, so reveal that control
        // instead of trying to fill the hidden input. Also leave any restored active conversation first: CI accounts
        // are durable staging users and must not depend on an empty browser/account history.
        Locator activeScope = page.getByLabel("Active conversation scope");
        if (activeScope.isVisible()) {
            page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("New chat").setExact(true)).click();
        }
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("Ask NevGiU").setExact(true))).isVisible();

        Locator candidateSearch = page.getByPlaceholder("Search candidate by name");
        if (!candidateSearch.isVisible()) {
            page.locator("details.manual-search > summary").click();
        }
        assertThat(candidateSearch).isVisible();
        candidateSearch.fill(query);
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true)).click();
        Locator result = page.locator("button.result").filter(
                new Locator.FilterOptions().setHas(page.getByText(exactCandidateName,
                        new Page.GetByTextOptions().setExact(true))));
        assertThat(result).hasCount(1);
        result.click();
        assertThat(page.getByRole(BUTTON,
                new Page.GetByRoleOptions().setName("Remove " + exactCandidateName).setExact(true))).isVisible();
    }

    public String startConversation() {
        page.getByRole(BUTTON,
                new Page.GetByRoleOptions().setName("Confirm scope and continue").setExact(true)).click();
        assertThat(page.getByLabel("Active conversation scope")).isVisible();
        Object storedId = page.evaluate("key => localStorage.getItem(key)", ACTIVE_CONVERSATION_KEY);
        if (!(storedId instanceof String id) || !id.matches("[0-9a-fA-F-]{36}")) {
            throw new AssertionError("The active conversation identifier was not persisted in browser storage");
        }
        return id;
    }

    public void deleteActiveConversation() {
        page.getByRole(BUTTON,
                new Page.GetByRoleOptions().setName("Delete chat").setExact(true)).click();
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("Ask NevGiU").setExact(true))).isVisible();
    }

    public void leaveActiveConversation() {
        page.getByRole(BUTTON,
                new Page.GetByRoleOptions().setName("New chat").setExact(true)).click();
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("Recent conversations").setExact(true))).isVisible();
    }

    public void resumeConversation(String candidateName, String expectedConversationId) {
        page.locator("button.recent-item")
                .filter(new Locator.FilterOptions().setHasText(candidateName))
                .first()
                .click();
        assertActiveConversation(candidateName, expectedConversationId);
    }

    public void reloadActiveConversation(String candidateName, String expectedConversationId) {
        page.reload();
        assertActiveConversation(candidateName, expectedConversationId);
    }

    public void askGroundedQuestion(String question) {
        page.getByLabel("Message").fill(question);
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true)).click();
        Locator citation = page.locator(".message.assistant .citations a").first();
        citation.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public void openFirstCitationAndVerifyTarget() {
        Locator citation = page.locator(".message.assistant .citations a").first();
        String href = citation.getAttribute("href");
        if (href == null || !href.matches("/candidates/import\\?candidateId=\\d+#cv-document-\\d+")) {
            throw new AssertionError("Expected a document citation URL but received: " + href);
        }
        String fragment = href.substring(href.indexOf('#') + 1);
        citation.click();
        page.waitForURL(url -> {
            URI navigated = URI.create(url);
            return navigated.getPath().equals("/candidates/import")
                    && navigated.getQuery() != null && navigated.getQuery().matches("candidateId=\\d+")
                    && fragment.equals(navigated.getFragment());
        });

        Locator target = page.locator("#" + fragment);
        assertThat(target).isVisible();
        Boolean inViewport = (Boolean) target.evaluate("element => {"
                + "const rect = element.getBoundingClientRect();"
                + "return rect.bottom > 0 && rect.top < window.innerHeight;"
                + "}");
        if (!Boolean.TRUE.equals(inViewport)) {
            throw new AssertionError("Citation target exists but was not scrolled into the viewport");
        }
    }

    public void assertNoActiveConversationPointer() {
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("Ask NevGiU").setExact(true))).isVisible();
        Object storedId = page.evaluate("key => localStorage.getItem(key)", ACTIVE_CONVERSATION_KEY);
        if (storedId != null) {
            throw new AssertionError("Deleting a conversation must clear its browser-local active pointer");
        }
    }

    private void assertActiveConversation(String candidateName, String expectedConversationId) {
        assertThat(page.getByLabel("Active conversation scope")).isVisible();
        assertThat(page.getByLabel("Active conversation scope").getByText(candidateName,
                new Locator.GetByTextOptions().setExact(true))).isVisible();
        Object storedId = page.evaluate("key => localStorage.getItem(key)", ACTIVE_CONVERSATION_KEY);
        if (!expectedConversationId.equals(storedId)) {
            throw new AssertionError("The resumed conversation did not restore the expected browser pointer");
        }
    }
}
