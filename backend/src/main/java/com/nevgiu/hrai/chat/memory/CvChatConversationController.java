package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/conversations")
class CvChatConversationController {
    private final CvChatConversationService service;

    CvChatConversationController(CvChatConversationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CvChatConversationResponse create(@Valid @RequestBody CvChatConversationRequests.Create request,
                                      @AuthenticationPrincipal AppUserPrincipal actor) {
        return service.create(request, actor);
    }

    @GetMapping("/{id}")
    CvChatConversationResponse get(@PathVariable String id,
                                   @AuthenticationPrincipal AppUserPrincipal actor) {
        return service.get(id, actor);
    }

    @PutMapping("/{id}/scope")
    CvChatConversationResponse replaceScope(@PathVariable String id,
                                            @Valid @RequestBody CvChatConversationRequests.ReplaceScope request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return service.replaceScope(id, request, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id, @AuthenticationPrincipal AppUserPrincipal actor) {
        service.delete(id, actor);
    }
}
