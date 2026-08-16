package com.nevgiu.hrai.job;

import com.nevgiu.hrai.job.dto.ApproveJobRequest;
import com.nevgiu.hrai.job.dto.JobGenerationRequest;
import com.nevgiu.hrai.job.dto.JobGenerationResponse;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobGenerationService jobGenerationService;
    private final JobRepository jobRepository;

    public JobController(JobGenerationService jobGenerationService, JobRepository jobRepository) {
        this.jobGenerationService = jobGenerationService;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/generate")
    public JobGenerationResponse generate(@RequestBody JobGenerationRequest request) {
        return jobGenerationService.generateJobOffer(request);
    }

    @PostMapping("/approve")
    public Job approve(@RequestBody ApproveJobRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return jobGenerationService.approveJob(request, principal.organizationId());
    }

    @GetMapping
    public Iterable<Job> findAll(@AuthenticationPrincipal AppUserPrincipal principal) {
        return jobRepository.findAllByOrganizationId(principal.organizationId());
    }

    @GetMapping("/{id}")
    public Job findById(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return jobRepository.findByIdAndOrganizationId(id, principal.organizationId()).orElseThrow();
    }
}
