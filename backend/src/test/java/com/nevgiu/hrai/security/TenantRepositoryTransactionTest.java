package com.nevgiu.hrai.security;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.job.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTransactionTest {

    @Test
    void tenantQueriesThatHydrateLobFieldsAreReadOnlyTransactional() throws Exception {
        assertReadOnlyTransaction(CandidateRepository.class, "findAllByOrganizationId", String.class);
        assertReadOnlyTransaction(CandidateRepository.class, "findByIdAndOrganizationId", Long.class, String.class);
        assertReadOnlyTransaction(JobRepository.class, "findAllByOrganizationId", String.class);
        assertReadOnlyTransaction(JobRepository.class, "findByIdAndOrganizationId", Long.class, String.class);
        assertReadOnlyTransaction(CvDocumentRepository.class, "findByOrganizationIdAndSha256",
                String.class, String.class);
        assertReadOnlyTransaction(CandidateEvaluationRepository.class, "findAllByOrganizationId", String.class);
    }

    private void assertReadOnlyTransaction(Class<?> repository, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = repository.getMethod(methodName, parameterTypes);
        Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transaction)
                .as("%s.%s must declare a transaction", repository.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transaction.readOnly())
                .as("%s.%s must use a read-only transaction", repository.getSimpleName(), methodName)
                .isTrue();
    }
}
