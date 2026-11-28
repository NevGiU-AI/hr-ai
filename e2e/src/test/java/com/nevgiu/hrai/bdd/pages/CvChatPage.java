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

    public void askForExtractionStatus() {
        // The canonical OCR wording uses deterministic operational routing, so this creates history without a paid call.
        askOperationalQuestion("Was this CV processed with OCR?", Pattern.compile("Text extraction used"));
    }

    public void askOperationalQuestion(String question, Pattern expectedContent) {
        Locator replies = page.locator(".timeline .message.assistant:not(.welcome-message)");
        int priorReplies = replies.count();
        page.getByLabel("Message").fill(question);
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true)).click();
        assertThat(replies).hasCount(priorReplies + 1);
        Locator reply = replies.nth(priorReplies);
        assertThat(reply.locator(".message-outcome")).hasText("Operational information");
        assertThat(reply.locator(".message-content")).containsText(expectedContent);
        assertThat(page.locator(".timeline .message.recruiter .message-content").last()).hasText(question);
    }

    public void assertOperationalHistory(String candidateName, String conversationId) {
        assertActiveConversation(candidateName, conversationId);
        assertConfirmedScopeSize(1);
        Locator timeline = page.locator(".timeline");
        Locator replies = timeline.locator(".message.assistant:not(.welcome-message)");
        assertThat(timeline.locator(".message.recruiter")).hasCount(3);
        assertThat(replies).hasCount(3);
        Pattern confirmedCandidate = Pattern.compile("^" + Pattern.quote(candidateName)
                + " \\(CANDIDATE-\\d+\\):");
        for (int index = 0; index < 3; index++) {
            // Pattern.quote emits Java's \Q…\E syntax, which the browser's regex engine does not support.
            // The replies are already present, so apply the pattern to their rendered text in Java.
            String content = replies.nth(index).locator(".message-content").innerText();
            if (!confirmedCandidate.matcher(content).lookingAt()) {
                throw new AssertionError("Operational reply " + (index + 1)
                        + " did not start with the confirmed candidate label");
            }
        }
        assertThat(timeline.locator(".citations")).hasCount(0);
    }

    public String addApprovedJob() {
        assertThat(page.getByLabel("Active conversation scope").locator(".job-chip")).hasCount(0);
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Edit scope").setExact(true)).click();
        Locator editor = page.getByLabel("Edit confirmed scope");
        Locator jobSelect = editor.getByLabel("Approved job context (optional)");
        // The first option is the no-job placeholder. Wait for the approved jobs to load before selecting one.
        Locator firstJob = jobSelect.locator("option").nth(1);
        firstJob.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        String label = firstJob.innerText().trim();
        String jobTitle = label.substring(0, label.lastIndexOf(" · "));
        jobSelect.selectOption(firstJob.getAttribute("value"));
        editor.getByRole(BUTTON,
                new Locator.GetByRoleOptions().setName("Save scope and clear history").setExact(true)).click();
        assertThat(editor).hasCount(0);
        return jobTitle;
    }

    public void assertEditedScope(String candidateName, String conversationId, String jobTitle) {
        assertActiveConversation(candidateName, conversationId);
        Locator scope = page.getByLabel("Active conversation scope");
        assertThat(scope.locator(".scope-chip")).hasCount(1);
        assertThat(scope.locator(".job-chip")).hasText("Job: " + jobTitle);
        // The welcome message is application copy, not stored conversation history.
        assertThat(page.locator(".timeline .message:not(.welcome-message)")).hasCount(0);
        assertThat(page.locator(".timeline .welcome-message")).hasCount(1);
    }

    public void askGroundedQuestion(String question) {
        page.getByLabel("Message").fill(question);
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Send").setExact(true)).click();
        Locator citation = page.locator(".message.assistant .citations a").first();
        citation.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    public void discoverOrganizationCandidates(String question) {
        discover("All organization candidates", question);
    }

    public void discoverNamedCandidates(String question) {
        discover("Named candidates", question);
    }

    private void discover(String mode, String question) {
        var form = page.locator(".discovery-composer");
        form.getByLabel(mode, new Locator.GetByLabelOptions().setExact(true)).check();
        form.getByLabel("Candidate discovery request").fill(question);
        form.getByRole(BUTTON, new Locator.GetByRoleOptions().setName("Send").setExact(true)).click();
    }

    public void assertUnconfirmedNamedGroups() {
        var groups = namedGroups();
        assertThat(groups).hasCount(2);
        assertThat(groups.locator("h3")).hasCount(2);
        assertThat(groups.nth(0).locator("h3")).hasText(Pattern.compile("Matches for .+"));
        assertThat(groups.nth(1).locator("h3")).hasText(Pattern.compile("Matches for .+"));
        if (groups.nth(0).locator("h3").innerText().equalsIgnoreCase(groups.nth(1).locator("h3").innerText())) {
            throw new AssertionError("Named discovery must show two distinct requested names");
        }
        // This fixture puts the ambiguous reference first. Both groups must offer a choice.
        assertThat(groups.nth(0).locator("button.result").nth(1)).isVisible();
        assertThat(groups.nth(1).locator("button.result").first()).isVisible();
        assertThat(page.locator(".scope-confirmation")).hasCount(0);
        assertNoActiveConversationPointer();
    }

    public List<String> selectOneFromEachNamedGroup() {
        var groups = namedGroups();
        var first = groups.nth(0).locator("button.result").first();
        var second = groups.nth(1).locator("button.result").first();
        if (candidateId(first).equals(candidateId(second))) {
            throw new AssertionError("Named discovery fixture must offer distinct candidates as the first choices");
        }
        var selectedNames = List.of(first.locator("strong").innerText(), second.locator("strong").innerText());
        if (selectedNames.get(0).equals(selectedNames.get(1))) {
            throw new AssertionError("Named discovery fixture must have distinct selected display names");
        }
        first.click();
        second.click();
        assertNoActiveConversationPointer();
        return selectedNames;
    }

    public void assertNamedScopeAfterReload(List<String> names, String conversationId) {
        // Confirmation also submits the pending question; do not interrupt that write with a reload.
        assertThat(page.getByLabel("Message", new Page.GetByLabelOptions().setExact(true)))
                .isEnabled(new com.microsoft.playwright.assertions.LocatorAssertions.IsEnabledOptions()
                        .setTimeout(60_000));
        reloadActiveConversation(names.getFirst(), conversationId);
        assertThat(page.getByLabel("Active conversation scope").locator(".scope-chip"))
                .hasText(names.toArray(String[]::new));
    }

    private Locator namedGroups() {
        return page.getByLabel("Candidate confirmation cards").locator(".candidate-match-group");
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
        sendAndWaitForReply(question, endpoint);
    }

    public void selectControlledReplyLanguage(String language) {
        page.getByLabel("Language for evidence limits and refusals").selectOption(
                new com.microsoft.playwright.options.SelectOption().setLabel(language));
    }

    public void askAndVerifyControlledReply(String question, String endpoint, String language, String badge,
                                            String localizedOpening) {
        sendAndWaitForReply(question, endpoint);
        // The badge belongs to the English interface; the text and its accessibility language belong to the reply.
        Locator reply = assistantAnswers().last();
        assertThat(reply.locator(".message-outcome")).hasText(badge);
        Locator content = reply.locator(".message-content");
        assertThat(content).hasAttribute("lang", language);
        assertThat(content).containsText(localizedOpening);
        assertThat(reply.locator(".citations a")).hasCount(0);
    }

    private void sendAndWaitForReply(String question, String endpoint) {
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
            throw new AssertionError("Conversation message request returned HTTP " + response.status());
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
