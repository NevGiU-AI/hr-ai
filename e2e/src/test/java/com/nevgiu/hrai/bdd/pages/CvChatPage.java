package com.nevgiu.hrai.bdd.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

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
        page.getByPlaceholder("Search candidate by name").fill(query);
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
                new Page.GetByRoleOptions().setName("Start conversation").setExact(true)).click();
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
                new Page.GetByRoleOptions().setName("Start a conversation").setExact(true))).isVisible();
    }
}
