package com.finsight.application;

import com.finsight.ai.AiServiceClient;
import com.finsight.domain.model.AnswerResponse;
import com.finsight.domain.model.AskQuestionCommand;
import com.finsight.domain.model.EvidenceChunk;
import com.finsight.domain.model.RagTrace;
import com.finsight.domain.repository.RagTraceRepository;
import com.finsight.rag.EvidenceRetriever;
import com.finsight.rag.QueryUnderstandingService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisApplicationService {
    private final QueryUnderstandingService queryUnderstandingService;
    private final EvidenceRetriever evidenceRetriever;
    private final AiServiceClient aiServiceClient;
    private final RagTraceRepository ragTraceRepository;

    public AnalysisApplicationService(
            QueryUnderstandingService queryUnderstandingService,
            EvidenceRetriever evidenceRetriever,
            AiServiceClient aiServiceClient,
            RagTraceRepository ragTraceRepository
    ) {
        this.queryUnderstandingService = queryUnderstandingService;
        this.evidenceRetriever = evidenceRetriever;
        this.aiServiceClient = aiServiceClient;
        this.ragTraceRepository = ragTraceRepository;
    }

    public AnswerResponse ask(AskQuestionCommand command) {
        Instant startedAt = Instant.now();
        Map<String, Object> structuredQuery = queryUnderstandingService.parse(command);
        List<EvidenceChunk> candidates = evidenceRetriever.retrieve(command.question(), structuredQuery);
        List<EvidenceChunk> evidence = aiServiceClient.rerank(command.question(), candidates).stream()
                .limit(5)
                .toList();
        String answer = aiServiceClient.generateAnswer(command.question(), structuredQuery, evidence);
        RagTrace trace = new RagTrace(
                UUID.randomUUID().toString(),
                startedAt,
                structuredQuery,
                List.of("keyword-search", "metric-store", "risk-store", "rerank"),
                evidence.size(),
                Duration.between(startedAt, Instant.now()).toMillis()
        );
        ragTraceRepository.save(command.companySymbol(), command.question(), trace);
        return new AnswerResponse(answer, evidence, trace);
    }
}
