package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
import com.nevgiu.hrai.chat.CvChatCandidateSearchResponse;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.CvChatOrganizationDiscoveryService;
import com.nevgiu.hrai.evaluation.EvaluationHistoryService;
import com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Read-only Spring AI tools whose tenant boundary comes exclusively from the authenticated server-side principal.
 * Tool arguments deliberately contain no organization identifier, role, or authorization flag that a model could
 * invent or override.
 *
 * <p>Spring components are singleton-scoped by default. {@link RequestScope} instead gives each HTTP request its own
 * tool object and discards it after the response. Spring injects a scoped proxy into longer-lived collaborators and
 * resolves that proxy to the current request's instance only when a tool is used. Consequently no mutable tool
 * lifecycle or authenticated context can be shared between concurrent users or organizations.</p>
 */
@Component
@RequestScope
public class CvChatReadTools {
    private static final Set<String> CHAT_ROLES = Set.of("ROLE_ADMIN", "ROLE_RECRUITER", "ROLE_REVIEWER");
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_REQUIREMENT_FIELD_CHARACTERS = 2_000;
    private static final int MAX_EVALUATION_RESULTS = 5;
    // Match Job.title's persistence limit while still defending the model-facing result boundary independently.
    private static final int MAX_EVALUATION_JOB_TITLE_CHARACTERS = 255;

    private final CvChatCandidateResolutionService candidateResolution;
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;
    private final JobRepository jobs;
    private final EvaluationHistoryService evaluationHistory;
    private final CvChatToolExecutionGuard executionGuard;
    private final CvChatToolScope scope;
    private final CvChatToolDiscoveryState discoveryState;
    private final CvChatToolPlanningState planningState;
    private final CvChatOrganizationDiscoveryService organizationDiscovery;

    public CvChatReadTools(CvChatCandidateResolutionService candidateResolution, CandidateRepository candidates,
                           CvDocumentRepository documents, JobRepository jobs,
                           EvaluationHistoryService evaluationHistory,
                           CvChatToolExecutionGuard executionGuard, CvChatToolScope scope,
                           CvChatToolDiscoveryState discoveryState, CvChatToolPlanningState planningState,
                           CvChatOrganizationDiscoveryService organizationDiscovery) {
        this.candidateResolution = candidateResolution;
        this.candidates = candidates;
        this.documents = documents;
        this.jobs = jobs;
        this.evaluationHistory = evaluationHistory;
        this.executionGuard = executionGuard;
        this.scope = scope;
        this.discoveryState = discoveryState;
        this.planningState = planningState;
        this.organizationDiscovery = organizationDiscovery;
    }

    @Tool(name = "search_candidates", description = "Search the authenticated organization's candidates by a "
            + "partial name for exactly one person. Call separately for each person in a multi-candidate request; "
            + "never pass the full recruiter sentence or combine multiple names. Returns bounded minimal cards for "
            + "explicit recruiter selection; never select a candidate automatically when multiple cards are returned.")
    public CandidateSearchResult searchCandidates(
            @ToolParam(description = "At least two characters from the candidate's name") String query) {
        // Resolve and authorize the principal on the HTTP request thread. Spring Security context is thread-local and
        // must not be looked up later inside the executor's virtual thread.
        AppUserPrincipal actor = currentChatActor();
        CvChatCandidateSearchResponse response = executionGuard.execute(
                () -> candidateResolution.search(query, actor.organizationId()));
        List<CandidateCard> cards = response.candidates().stream().limit(MAX_SEARCH_RESULTS)
                .map(candidate -> new CandidateCard(candidate.id(), candidate.name(), candidate.currentTitle()))
                .toList();
        // The system prompt asks the model to search once per named person, but the tool result is the authoritative
        // fact. Record this authorized outcome so Java can prevent a later provider REFUSED result from hiding valid
        // cards—or prevent confirmation when one requested person was not found. This does not select any candidate.
        // Record counts only for deterministic postconditions; do not log or persist the query or returned cards.
        discoveryState.record(query, cards.size());
        return new CandidateSearchResult(cards,
                response.truncated() || response.candidates().size() > MAX_SEARCH_RESULTS);
    }

    @Tool(name = "search_candidates_by_evidence", description = "Search all indexed candidates in the authenticated "
            + "organization using job-relevant professional criteria. Returns at most ten ranked minimal candidate "
            + "cards with evidence references for explicit recruiter confirmation. Never treats ranking as an "
            + "autonomous hiring decision and never searches another organization.")
    public CandidateEvidenceSearchResult searchCandidatesByEvidence(
            @ToolParam(description = "Job-relevant skills, experience, education, or professional criteria") String query) {
        AppUserPrincipal actor = currentChatActor();
        return executionGuard.executeSemanticDiscovery(
                () -> organizationDiscovery.discover(query, actor.organizationId()));
    }

    @Tool(name = "get_candidate_summary", description = "Return a minimized, tenant-authorized summary for one "
            + "candidate ID that the recruiter already selected. Never returns raw CV text, contact details, "
            + "storage identifiers, content hashes, or metadata revision history.")
    public CandidateSummary getCandidateSummary(
            @ToolParam(description = "Explicitly selected candidate database ID") Long candidateId) {
        // Capture only the trusted tenant identifier before crossing the virtual-thread execution boundary. The model
        // supplies candidateId, but can never supply or replace actor.organizationId().
        AppUserPrincipal actor = currentChatActor();
        // Prompt instructions are not an authorization boundary. Reject any model-supplied ID that was not explicitly
        // authorized and installed into this request's confirmed scope by the orchestrator.
        scope.requireConfirmed(candidateId);
        CandidateSummary summary = executionGuard.execute(() -> {
            Candidate candidate = candidates.findByIdAndOrganizationId(candidateId, actor.organizationId())
                    // Missing and cross-tenant IDs intentionally share one response to prevent tenant enumeration.
                    .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
            CvDocument currentDocument = documents
                    .findAllByCandidateIdAndOrganizationId(candidateId, actor.organizationId())
                    .stream().findFirst().orElse(null);
            CurrentDocumentStatus documentStatus = currentDocument == null ? null : documentStatus(currentDocument);
            return new CandidateSummary(candidate.getId(), candidate.getName(), candidate.getCurrentTitle(),
                    candidate.getYearsExperience(), documentStatus);
        });
        planningState.recordCandidateSummary(candidateId);
        return summary;
    }

    @Tool(name = "get_job_requirements", description = "Return bounded requirements for one approved job that the "
            + "recruiter explicitly selected. Never returns tenant identifiers, salary, benefits, tone, or unrelated "
            + "job-generation metadata.")
    public JobRequirements getJobRequirements(
            @ToolParam(description = "Explicitly selected approved job database ID") Long jobId) {
        AppUserPrincipal actor = currentChatActor();
        // Tenant authorization alone is insufficient: require the application-owned human-confirmed job scope before
        // allowing a model-generated tool argument to reach the repository.
        scope.requireConfirmedJob(jobId);
        JobRequirements requirements = executionGuard.execute(() -> {
            Job job = jobs.findByIdAndOrganizationId(jobId, actor.organizationId())
                    .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Approved job not found"));
            return new JobRequirements(job.getId(), bounded(job.getTitle()), bounded(job.getLevel()),
                    bounded(job.getSummary()), bounded(job.getResponsibilities()),
                    bounded(job.getRequiredQualifications()), bounded(job.getPreferredQualifications()),
                    bounded(job.getSoftSkills()));
        });
        planningState.recordJobRequirements(jobId);
        return requirements;
    }

    @Tool(name = "get_evaluation_evidence", description = "Return at most five newest persisted evaluation results "
            + "for one candidate that the recruiter already selected. Includes bounded job context, scores, "
            + "timestamps, and current/stale state. Never returns generated rationale, candidate names, contact "
            + "details, raw CV text, tenant identifiers, storage identifiers, hashes, prompts, provider configuration, "
            + "or model settings. Persisted scores are human-review context, not metric-level CV source evidence.")
    public EvaluationEvidenceResult getEvaluationEvidence(
            @ToolParam(description = "Explicitly selected candidate database ID") Long candidateId) {
        AppUserPrincipal actor = currentChatActor();
        // Evaluation history is candidate data. Knowing an ID or receiving it from the model never authorizes access;
        // the application must first install that exact recruiter-confirmed ID in this request's scope.
        scope.requireConfirmed(candidateId);
        List<EvaluationEvidenceSnapshot> history = executionGuard.execute(() ->
                evaluationHistory.findRecentEvidenceForCandidate(
                        candidateId, actor.organizationId(), MAX_EVALUATION_RESULTS + 1));
        boolean truncated = history.size() > MAX_EVALUATION_RESULTS;
        List<EvaluationEvidenceItem> boundedHistory = history.stream()
                .limit(MAX_EVALUATION_RESULTS)
                .map(this::evaluationEvidence)
                .toList();
        return new EvaluationEvidenceResult(candidateId, boundedHistory, truncated);
    }

    @Tool(name = "get_document_status", description = "Return minimized processing state for the newest CV document "
            + "of one candidate that the recruiter already selected. Never returns filenames, raw errors, CV text, "
            + "tenant identifiers, storage identifiers, hashes, or original-file metadata.")
    public DocumentStatusResult getDocumentStatus(
            @ToolParam(description = "Explicitly selected candidate database ID") Long candidateId) {
        AppUserPrincipal actor = currentChatActor();
        scope.requireConfirmed(candidateId);
        CvDocument document = executionGuard.execute(() -> currentDocument(candidateId, actor.organizationId()));
        return new DocumentStatusResult(candidateId, document == null ? null : documentStatus(document));
    }

    @Tool(name = "get_ingestion_warnings", description = "Return application-owned processing warning codes for "
            + "the newest CV document of one candidate that the recruiter already selected. Never returns raw parser, "
            + "OCR, malware-scanner, provider, filename, storage, hash, or CV-content details.")
    public IngestionWarningsResult getIngestionWarnings(
            @ToolParam(description = "Explicitly selected candidate database ID") Long candidateId) {
        AppUserPrincipal actor = currentChatActor();
        scope.requireConfirmed(candidateId);
        return executionGuard.execute(() -> {
            CvDocument document = currentDocument(candidateId, actor.organizationId());
            if (document == null) {
                return new IngestionWarningsResult(candidateId, null,
                        List.of(IngestionWarning.NO_DOCUMENT_AVAILABLE));
            }
            List<IngestionWarning> warnings = new ArrayList<>();
            if (document.getStatus() == CvIngestionStatus.NEEDS_REVIEW) {
                warnings.add(IngestionWarning.MANUAL_REVIEW_REQUIRED);
            }
            if (document.getStatus() == CvIngestionStatus.FAILED) {
                warnings.add(IngestionWarning.INGESTION_FAILED);
            }
            // Raw ingestion errors may contain parser/provider internals. Report only that an additional warning was
            // recorded, and avoid duplicating the already explicit FAILED or NEEDS_REVIEW status codes.
            if (document.getIngestionError() != null && !document.getIngestionError().isBlank()
                    && warnings.isEmpty()) {
                warnings.add(IngestionWarning.PROCESSING_WARNING_RECORDED);
            }
            return new IngestionWarningsResult(candidateId, document.getId(), warnings);
        });
    }

    private CvDocument currentDocument(Long candidateId, String organizationId) {
        // Resolve the candidate first so a missing/cross-tenant candidate never becomes distinguishable from an empty
        // document history. The repository's organization predicate is repeated for the document query.
        candidates.findByIdAndOrganizationId(candidateId, organizationId)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
        return documents.findAllByCandidateIdAndOrganizationId(candidateId, organizationId)
                .stream().findFirst().orElse(null);
    }

    private CurrentDocumentStatus documentStatus(CvDocument document) {
        return new CurrentDocumentStatus(document.getId(), document.getStatus(), document.getExtractionMethod(),
                document.getImportedAt(), document.getReprocessedAt());
    }

    private EvaluationEvidenceItem evaluationEvidence(EvaluationEvidenceSnapshot history) {
        EvaluationScores scores = new EvaluationScores(
                history.skillsMatchScore(), history.experienceRelevanceScore(), history.educationFitScore(),
                history.achievementImpactScore(), history.keywordDensityScore(), history.employmentGapScore(),
                history.readabilityScore(), history.aiConfidenceScore(), history.overallFitScore());
        return new EvaluationEvidenceItem(history.evaluationId(), history.jobId(),
                bounded(history.jobTitle(), MAX_EVALUATION_JOB_TITLE_CHARACTERS), scores, history.createdAt(),
                history.invalidatedAt(), history.current());
    }

    private String bounded(String value) {
        return bounded(value, MAX_REQUIREMENT_FIELD_CHARACTERS);
    }

    private String bounded(String value, int maximumCharacters) {
        if (value == null || value.length() <= maximumCharacters) {
            return value;
        }
        return value.substring(0, maximumCharacters);
    }

    private AppUserPrincipal currentChatActor() {
        // Endpoint authorization remains the outer boundary; this independent check protects future non-controller
        // callers and prevents a tool callback from becoming a role-policy bypass.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)) {
            throw new CvChatException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean permitted = actor.getAuthorities().stream()
                .anyMatch(authority -> CHAT_ROLES.contains(authority.getAuthority()));
        if (!permitted) {
            throw new CvChatException(HttpStatus.FORBIDDEN, "CV chat access denied");
        }
        return actor;
    }
}
