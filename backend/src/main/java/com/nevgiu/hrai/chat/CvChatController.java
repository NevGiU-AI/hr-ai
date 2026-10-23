package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/candidates")
public class CvChatController {
    private final CvChatService service;

    public CvChatController(CvChatService service) {
        this.service = service;
    }

    /**
     * Answers one question about an explicitly selected candidate.
     *
     * <p>The organization is always taken from the authenticated server-side principal. It is intentionally absent
     * from the request body so a caller or the model cannot select a different tenant.</p>
     */
    @PostMapping("/{candidateId}")
    public CvChatResponse ask(@PathVariable Long candidateId, @Valid @RequestBody CvChatRequest request,
                              @AuthenticationPrincipal AppUserPrincipal actor) {
        return service.ask(candidateId, request.message(), actor.organizationId());
    }
}
