package com.nevgiu.hrai.bdd.steps;

import com.microsoft.playwright.APIRequestContext;
import com.nevgiu.hrai.bdd.config.BddConfiguration.Credentials;
import com.nevgiu.hrai.bdd.context.ScenarioContext;
import com.nevgiu.hrai.bdd.pages.LoginPage;
import io.cucumber.java.en.Then;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Read-only authorization checks against a conversation created by this scenario. */
public class ConversationTenantIsolationSteps {
    private final ScenarioContext context;
    private final ObjectMapper json = new ObjectMapper();

    public ConversationTenantIsolationSteps(ScenarioContext context) {
        this.context = context;
    }

    @Then("the conversation and its history are available only to the owner in the two-organization fixture")
    public void verifyTenantBoundary() {
        var credentials = new Credentials(required("BDD_OTHER_TENANT_EMAIL"), required("BDD_OTHER_TENANT_PASSWORD"));
        var other = context.browser().newContext();
        context.otherTenantBrowserContext(other);
        var page = other.newPage();
        context.otherTenantPage(page);
        new LoginPage(page, context.configuration().baseUrl()).signIn(credentials);

        var owner = context.recruiterBrowserContext().request();
        var otherTenant = other.request();
        // Validate the real server-side organizations so an accidentally reused account cannot pass as cross-tenant.
        assertThat(organizationId(owner).equals(organizationId(otherTenant)))
                .as("the two authenticated test accounts must belong to different organizations").isFalse();

        String conversation = context.configuration().apiUrl() + "/chat/conversations/" + context.conversationId();
        for (String suffix : List.of("", "/messages")) {
            String endpoint = conversation + suffix;
            String resource = suffix.isEmpty() ? "conversation metadata" : "message history";
            // The positive control rules out a stale conversation ID or wrong endpoint.
            assertThat(owner.get(endpoint).status()).as("owner can read %s", resource).isEqualTo(200);
            var denied = otherTenant.get(endpoint);
            assertThat(denied.status()).as("other organization cannot read %s", resource).isEqualTo(404);
            var error = json.readTree(denied.body());
            assertThat(error.path("status").asInt())
                    .as("opaque not-found response for %s", resource).isEqualTo(404);
            for (String field : List.of("candidates", "job", "version", "expiresAt", "messages", "content", "citations")) {
                assertThat(error.has(field)).as("not-found response does not disclose %s", field).isFalse();
            }
        }
    }

    private String organizationId(APIRequestContext session) {
        var response = session.get(context.configuration().apiUrl() + "/auth/me");
        assertThat(response.status()).as("fixture account is authenticated").isEqualTo(200);
        String organization = json.readTree(response.body()).path("organizationId").asText("");
        assertThat(organization.isBlank()).as("authenticated account has an organization").isFalse();
        return organization;
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required BDD variable: " + key);
        return value;
    }
}
