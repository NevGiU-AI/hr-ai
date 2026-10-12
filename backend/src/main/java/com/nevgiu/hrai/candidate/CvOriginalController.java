package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/cv-documents")
public class CvOriginalController {
    private final CvOriginalService originals;

    public CvOriginalController(CvOriginalService originals) {
        this.originals = originals;
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
}
