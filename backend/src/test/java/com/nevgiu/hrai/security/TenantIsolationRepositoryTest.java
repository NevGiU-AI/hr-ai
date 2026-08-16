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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TenantIsolationRepositoryTest {

    @Autowired CandidateRepository candidates;
    @Autowired JobRepository jobs;
    @Autowired CandidateEvaluationRepository evaluations;

    @Test
    void scopesCandidatesJobsAndEvaluationsToTheirOrganization() {
        Candidate candidateA = candidates.save(candidate("tenant-a", "Ada"));
        Candidate candidateB = candidates.save(candidate("tenant-b", "Grace"));
        Job jobA = jobs.save(job("tenant-a", "Backend Engineer"));
        Job jobB = jobs.save(job("tenant-b", "Platform Engineer"));

        CandidateEvaluation evaluationA = new CandidateEvaluation();
        evaluationA.setOrganizationId("tenant-a");
        evaluationA.setCandidate(candidateA);
        evaluationA.setJob(jobA);
        evaluations.save(evaluationA);

        CandidateEvaluation evaluationB = new CandidateEvaluation();
        evaluationB.setOrganizationId("tenant-b");
        evaluationB.setCandidate(candidateB);
        evaluationB.setJob(jobB);
        evaluations.save(evaluationB);

        assertThat(candidates.findAllByOrganizationId("tenant-a")).extracting(Candidate::getName)
                .containsExactly("Ada");
        assertThat(candidates.findByIdAndOrganizationId(candidateB.getId(), "tenant-a")).isEmpty();
        assertThat(jobs.findAllByOrganizationId("tenant-a")).extracting(Job::getTitle)
                .containsExactly("Backend Engineer");
        assertThat(jobs.findByIdAndOrganizationId(jobB.getId(), "tenant-a")).isEmpty();
        assertThat(evaluations.findAllByOrganizationId("tenant-a")).containsExactly(evaluationA);
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
