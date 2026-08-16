package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.ingestion.CvIngestionService;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import com.nevgiu.hrai.candidate.ingestion.dto.CvImportResult;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateRepository candidateRepository;
    private final CvIngestionService cvIngestionService;

    public CandidateController(CandidateRepository candidateRepository, CvIngestionService cvIngestionService) {
        this.candidateRepository = candidateRepository;
        this.cvIngestionService = cvIngestionService;
    }

    @GetMapping
    public Iterable<Candidate> findAll(@AuthenticationPrincipal AppUserPrincipal principal) {
        return candidateRepository.findAllByOrganizationId(principal.organizationId());
    }

    @PostMapping
    public Candidate create(@RequestBody Candidate candidate, @AuthenticationPrincipal AppUserPrincipal principal) {
        candidate.setId(null);
        candidate.setOrganizationId(principal.organizationId());
        return candidateRepository.save(candidate);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CvImportResult> importPdf(@RequestPart("file") MultipartFile file,
                                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cvIngestionService.importPdf(file, principal.organizationId()));
    }

    @PostMapping(value = "/import/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CvArchiveImportResult importArchive(@RequestPart("file") MultipartFile file,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return cvIngestionService.importArchive(file, principal.organizationId());
    }

    @PostMapping("/import/initial")
    public CvArchiveImportResult importInitialArchive(@AuthenticationPrincipal AppUserPrincipal principal) {
        return cvIngestionService.importInitialArchive(principal.organizationId());
    }
}
