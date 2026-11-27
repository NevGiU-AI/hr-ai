package com.nevgiu.hrai.bdd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
        assertThat(page.getByLabel("Start a conversation")).isVisible();

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
        assertThat(page.getByLabel("Start a conversation")).isVisible();
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

    public void discoverOrganizationCandidates(String question) {
        var form = page.locator(".discovery-composer");
        form.getByLabel("All organization candidates").check();
        form.getByLabel("Candidate discovery request").fill(question);
        form.getByRole(BUTTON, new Locator.GetByRoleOptions().setName("Send").setExact(true)).click();
    }

    public void assertOrganizationShortlistUnconfirmed() {
        // Require an unselected control card so this scenario exercises a real subset, not the whole shortlist.
        assertThat(organizationCards().nth(2)).isVisible();
        assertThat(page.locator(".scope-confirmation")).hasCount(0);
        assertNoActiveConversationPointer();
    }

    public List<String> selectFirstTwoOrganizationMatches() {
        var cards = organizationCards();
        String first = candidateId(cards.nth(0));
        String second = candidateId(cards.nth(1));
        if (first.equals(second)) throw new AssertionError("Discovery returned duplicate candidate cards");
        cards.nth(0).click();
        cards.nth(1).click();
        return List.of(first, second);
    }

    public void assertConfirmedScopeSize(int count) {
        assertThat(page.getByLabel("Active conversation scope").locator(".scope-chip")).hasCount(count);
    }

    public void askComparison(String question, String endpoint) {
        var message = page.getByLabel("Message", new Page.GetByLabelOptions().setExact(true));
        // Confirmation may automatically submit the discovery text. Wait for it before testing the next turn.
        assertThat(message).isEnabled(new com.microsoft.playwright.assertions.LocatorAssertions.IsEnabledOptions()
                .setTimeout(60_000));
        int priorAnswers = assistantAnswers().count();
        message.fill(question);
        // Wait for the scoped POST, not a five-second DOM poll while retrieval and generation are still running.
        var response = page.waitForResponse(
                result -> endpoint.equals(result.url()) && "POST".equals(result.request().method()),
                new Page.WaitForResponseOptions().setTimeout(60_000),
                () -> page.locator("form.composer").getByRole(BUTTON,
                        new Locator.GetByRoleOptions().setName("Send").setExact(true)).click());
        if (response.status() != 200) {
            throw new AssertionError("Comparison message request returned HTTP " + response.status());
        }
        assertThat(assistantAnswers()).hasCount(priorAnswers + 1);
    }

    public void assertLatestCitationCandidateIds(List<String> expected) {
        Set<String> actual = new HashSet<>();
        var latest = assistantAnswers().last();
        if (latest.locator(".message-outcome").isVisible()) {
            throw new AssertionError("Expected a grounded comparison but got: "
                    + latest.locator(".message-outcome").innerText());
        }
        var links = latest.locator(".citations a");
        var candidateInLink = Pattern.compile("[?&]candidateId=(\\d+)(?:[&#]|$)");
        for (var link : links.all()) {
            String href = link.getAttribute("href");
            var match = candidateInLink.matcher(href == null ? "" : href);
            if (!match.find()) throw new AssertionError("Citation is missing a candidate identifier");
            actual.add(match.group(1));
        }
        if (!actual.equals(Set.copyOf(expected))) {
            throw new AssertionError("Comparison citations did not cover exactly the confirmed candidates");
        }
    }

    private Locator organizationCards() {
        return page.getByLabel("Candidate confirmation cards").locator("button.result");
    }

    private Locator assistantAnswers() {
        return page.getByLabel("Conversation timeline").locator(".message.assistant:not(.welcome-message)");
    }

    private static String candidateId(Locator card) {
        var match = Pattern.compile("#(\\d+)\\s*$").matcher(card.innerText());
        if (!match.find()) throw new AssertionError("Discovery card is missing a candidate identifier");
        return match.group(1);
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
        try {
            // The document card can become visible just before Angular's deferred scrollIntoView callback runs.
            // Poll the observable browser outcome instead of checking in the small race window between rendering
            // and scrolling. The bounded timeout still fails when citation navigation genuinely does not scroll.
            page.waitForFunction("fragment => {"
                            + "const element = document.getElementById(fragment);"
                            + "if (!element) return false;"
                            + "const rect = element.getBoundingClientRect();"
                            + "return rect.bottom > 0 && rect.top < window.innerHeight;"
                            + "}",
                    fragment,
                    new Page.WaitForFunctionOptions()
                            .setPollingInterval(50)
                            .setTimeout(5_000));
        } catch (TimeoutError error) {
            throw new AssertionError("Citation target exists but was not scrolled into the viewport", error);
        }
    }

    public void assertNoActiveConversationPointer() {
        assertThat(page.getByLabel("Start a conversation")).isVisible();
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
