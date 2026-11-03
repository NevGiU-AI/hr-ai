package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatToolOrchestrationServiceTest {
    private final CvChatEvidenceService evidence = mock(CvChatEvidenceService.class);
    private final CvChatReadToolRegistry registry = mock(CvChatReadToolRegistry.class);
    private final CvChatToolScope scope = mock(CvChatToolScope.class);
    private final CvChatToolOrchestrationModel model = mock(CvChatToolOrchestrationModel.class);
    private final CvChatToolOrchestrationService service =
            new CvChatToolOrchestrationService(evidence, registry, scope, model);

    @Test
    void discoveryExposesOnlyDiscoveryCallbacksWithoutReadingCandidateEvidence() {
        List<ToolCallback> callbacks = List.of(mock(ToolCallback.class));
        when(registry.discoveryCallbacks()).thenReturn(callbacks);
        when(model.respond("Find Ada and Grace", callbacks)).thenReturn(" Candidate cards found. ");

        assertThat(service.discover("  Find Ada and Grace  ")).isEqualTo("Candidate cards found.");

        verify(model).respond("Find Ada and Grace", callbacks);
        verifyNoInteractions(evidence, scope);
    }

    @Test
    void authorizesEveryCandidateBeforeInstallingConfirmedScopeAndCallingTheModel() {
        List<ToolCallback> callbacks = List.of(mock(ToolCallback.class));
        List<Long> candidateIds = List.of(7L, 9L);
        when(registry.confirmedScopeCallbacks()).thenReturn(callbacks);
        when(model.respond("Compare their scope", callbacks)).thenReturn("Ready for grounded comparison.");

        assertThat(service.planConfirmedScope("Compare their scope", candidateIds, "tenant-a"))
                .isEqualTo("Ready for grounded comparison.");

        InOrder order = inOrder(evidence, scope, model);
        order.verify(evidence).authorizeCandidate(7L, "tenant-a");
        order.verify(evidence).authorizeCandidate(9L, "tenant-a");
        order.verify(scope).confirm(candidateIds);
        order.verify(model).respond("Compare their scope", callbacks);
    }

    @Test
    void failedTenantAuthorizationPreventsScopeInstallationAndModelAccess() {
        CvChatException notFound = new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found");
        org.mockito.Mockito.doThrow(notFound).when(evidence).authorizeCandidate(9L, "tenant-a");

        assertThatThrownBy(() -> service.planConfirmedScope("Compare", List.of(7L, 9L), "tenant-a"))
                .isSameAs(notFound);

        verify(scope, never()).confirm(org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(model);
    }

    @Test
    void rejectsInvalidInputBeforeAuthorizationOrProviderAccess() {
        assertThatThrownBy(() -> service.discover("  "))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Chat message is required");
        assertThatThrownBy(() -> service.planConfirmedScope("Compare", List.of(7L, 7L), "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Confirm between 1 and 5 distinct candidates");

        verifyNoInteractions(evidence, scope, model);
    }

    @Test
    void convertsProviderFailuresAndEmptyResponsesToStableSafeErrors() {
        List<ToolCallback> callbacks = List.of(mock(ToolCallback.class));
        when(registry.discoveryCallbacks()).thenReturn(callbacks);
        when(model.respond("Find Ada", callbacks)).thenThrow(new IllegalStateException("provider secret"));

        assertThatThrownBy(() -> service.discover("Find Ada"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool orchestration is unavailable");

        when(model.respond("Find Grace", callbacks)).thenReturn(" ");
        assertThatThrownBy(() -> service.discover("Find Grace"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider returned an empty response");
    }
}
