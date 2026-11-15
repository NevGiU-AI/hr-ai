package com.nevgiu.hrai.bdd.pages;

import com.microsoft.playwright.Page;
import com.nevgiu.hrai.bdd.config.BddConfiguration.Credentials;

import java.net.URI;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.options.AriaRole.BUTTON;
import static com.microsoft.playwright.options.AriaRole.HEADING;

/** Accessible browser actions for the application login page. */
public class LoginPage {
    private final Page page;
    private final URI baseUrl;

    public LoginPage(Page page, URI baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public void signIn(Credentials credentials) {
        page.navigate(baseUrl + "/login");
        assertThat(page.getByRole(HEADING,
                new Page.GetByRoleOptions().setName("Sign in to HR AI"))).isVisible();
        page.getByLabel("Email").fill(credentials.email());
        page.getByLabel("Password", new Page.GetByLabelOptions().setExact(true)).fill(credentials.password());
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
        page.waitForURL(url -> !URI.create(url).getPath().equals("/login"));
    }
}
