package com.finsight.api;

import com.finsight.application.AnalysisApplicationService;
import com.finsight.domain.model.AnswerResponse;
import com.finsight.domain.model.AskQuestionCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisApplicationService analysisApplicationService;

    public AnalysisController(AnalysisApplicationService analysisApplicationService) {
        this.analysisApplicationService = analysisApplicationService;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskQuestionCommand command) {
        return analysisApplicationService.ask(command);
    }
}

