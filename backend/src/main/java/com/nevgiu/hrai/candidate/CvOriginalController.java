package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/cv-documents")
public class CvOriginalController {
    private final CvOriginalService originals;
    private final CvLegalHoldService legalHolds;
    private final CvRetentionService retention;

    public CvOriginalController(CvOriginalService originals, CvLegalHoldService legalHolds,
                                CvRetentionService retention) {
        this.originals = originals;
        this.legalHolds = legalHolds;
        this.retention = retention;
    }

    @GetMapping("/{documentId}/original")
    public ResponseEntity<byte[]> download(@PathVariable Long documentId,
                                           @AuthenticationPrincipal AppUserPrincipal actor) {
        CvOriginalDownload download = originals.download(documentId, actor);
        return ResponseEntity.ok()
                // Originals contain personal data and must not remain in shared browser or intermediary caches.
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .body(download.content());
    }

    @DeleteMapping("/{documentId}/original")
    public ResponseEntity<Void> delete(@PathVariable Long documentId,
                                       @AuthenticationPrincipal AppUserPrincipal actor) {
        originals.delete(documentId, actor);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{documentId}/legal-hold")
    public ResponseEntity<Void> updateLegalHold(@PathVariable Long documentId,
                                                @Valid @RequestBody UpdateLegalHoldRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        boolean expiredAfterRelease = legalHolds.update(documentId, request.held(), actor);
        if (expiredAfterRelease) {
            retention.expire(documentId);
        }
        return ResponseEntity.noContent().build();
    }
}
