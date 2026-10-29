package com.nevgiu.hrai.security;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.evaluation.CandidateEvaluation;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class TenantIsolationRepositoryTest {

    @Autowired CandidateRepository candidates;
    @Autowired JobRepository jobs;
    @Autowired CandidateEvaluationRepository evaluations;

    @Test
    void scopesCandidatesJobsAndEvaluationsToTheirOrganization() {
        Candidate olderCandidateA = candidate("tenant-a", "Ada");
        olderCandidateA.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        olderCandidateA = candidates.save(olderCandidateA);
        Candidate newerCandidateA = candidate("tenant-a", "Katherine");
        newerCandidateA.setCreatedAt(Instant.parse("2026-01-02T10:00:00Z"));
        newerCandidateA = candidates.save(newerCandidateA);
        Candidate candidateB = candidates.save(candidate("tenant-b", "Grace"));
        Job jobA = jobs.save(job("tenant-a", "Backend Engineer"));
        Job jobB = jobs.save(job("tenant-b", "Platform Engineer"));

        CandidateEvaluation evaluationA = new CandidateEvaluation();
        evaluationA.setOrganizationId("tenant-a");
        evaluationA.setCandidate(olderCandidateA);
        evaluationA.setJob(jobA);
        evaluations.save(evaluationA);

        CandidateEvaluation evaluationB = new CandidateEvaluation();
        evaluationB.setOrganizationId("tenant-b");
        evaluationB.setCandidate(candidateB);
        evaluationB.setJob(jobB);
        evaluations.save(evaluationB);

        assertThat(candidates.findAllByOrganizationIdOrderByCreatedAtDescIdDesc("tenant-a"))
                .extracting(Candidate::getName)
                .containsExactly("Katherine", "Ada");
        assertThat(candidates.findByIdAndOrganizationId(candidateB.getId(), "tenant-a")).isEmpty();
        assertThat(jobs.findAllByOrganizationId("tenant-a")).extracting(Job::getTitle)
                .containsExactly("Backend Engineer");
        assertThat(jobs.findByIdAndOrganizationId(jobB.getId(), "tenant-a")).isEmpty();
        assertThat(evaluations.findAllByOrganizationId("tenant-a")).containsExactly(evaluationA);
        assertThat(evaluations.findHistoryByCandidateIdAndOrganizationId(olderCandidateA.getId(), "tenant-a"))
                .extracting(com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem::id)
                .containsExactly(evaluationA.getId());
        assertThat(evaluations.findHistoryByCandidateIdAndOrganizationId(
                candidateB.getId(), "tenant-a")).isEmpty();
    }

    private Candidate candidate(String organizationId, String name) {
        Candidate candidate = new Candidate();
        candidate.setOrganizationId(organizationId);
        candidate.setName(name);
        return candidate;
    }

    private Job job(String organizationId, String title) {
        Job job = new Job();
        job.setOrganizationId(organizationId);
        job.setTitle(title);
        return job;
    }
}
