package com.nevgiu.hrai.security;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTransactionTest {

    @Test
    void tenantQueriesThatHydrateLobFieldsAreReadOnlyTransactional() throws Exception {
        assertReadOnlyTransaction(CandidateRepository.class,
                "findAllByOrganizationIdOrderByCreatedAtDescIdDesc", String.class);
        assertReadOnlyTransaction(CandidateRepository.class, "findByIdAndOrganizationId", Long.class, String.class);
        assertReadOnlyTransaction(JobRepository.class, "findAllByOrganizationId", String.class);
        assertReadOnlyTransaction(JobRepository.class, "findByIdAndOrganizationId", Long.class, String.class);
        assertReadOnlyTransaction(CvDocumentRepository.class, "findByOrganizationIdAndSha256",
                String.class, String.class);
        assertReadOnlyTransaction(CvDocumentPageRepository.class,
                "findAllByDocumentIdOrderByPageNumber", Long.class);
        assertReadOnlyTransaction(CandidateEvaluationRepository.class, "findAllByOrganizationId", String.class);
        assertReadOnlyTransaction(CandidateEvaluationRepository.class,
                "findHistoryByCandidateIdAndOrganizationId", Long.class, String.class);
    }

    @Test
    void semanticSearchSuspendsJpaTransactionsDuringEmbeddingAndVectorIo() throws Exception {
        Method method = CvSemanticSearchService.class.getMethod(
                "search", String.class, String.class, java.util.List.class, int.class);
        Transactional transaction = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
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
