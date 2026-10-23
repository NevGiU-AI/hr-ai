package com.nevgiu.hrai.evaluation;

import com.nevgiu.hrai.evaluation.dto.EvaluationRequest;
import com.nevgiu.hrai.evaluation.dto.EvaluationResponse;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final CvEvaluationService cvEvaluationService;
    private final EvaluationHistoryService evaluationHistoryService;

    public EvaluationController(CvEvaluationService cvEvaluationService,
                                EvaluationHistoryService evaluationHistoryService) {
        this.cvEvaluationService = cvEvaluationService;
        this.evaluationHistoryService = evaluationHistoryService;
    }

    @GetMapping
    public List<EvaluationHistoryItem> history(@RequestParam Long candidateId,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return evaluationHistoryService.findForCandidate(candidateId, principal.organizationId());
    }

    @PostMapping
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return cvEvaluationService.evaluateCandidate(request, principal.organizationId());
    }
}
