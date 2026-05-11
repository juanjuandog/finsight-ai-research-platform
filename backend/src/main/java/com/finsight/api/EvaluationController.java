package com.finsight.api;

import com.finsight.application.RagEvaluationService;
import com.finsight.evaluation.EvaluationCase;
import com.finsight.evaluation.EvaluationRun;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations/rag")
public class EvaluationController {
    private final RagEvaluationService ragEvaluationService;

    public EvaluationController(RagEvaluationService ragEvaluationService) {
        this.ragEvaluationService = ragEvaluationService;
    }

    @GetMapping("/cases")
    public List<EvaluationCase> cases() {
        return ragEvaluationService.cases();
    }

    @PostMapping("/run")
    public EvaluationRun run() {
        return ragEvaluationService.run();
    }
}

