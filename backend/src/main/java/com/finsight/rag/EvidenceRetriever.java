package com.finsight.rag;

import com.finsight.domain.model.DocumentType;
import com.finsight.domain.model.EvidenceChunk;
import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.MetricRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceRetriever {
    private final MetricRepository metricRepository;
    private final HybridRetrievalGateway retrievalGateway;

    public EvidenceRetriever(MetricRepository metricRepository, HybridRetrievalGateway retrievalGateway) {
        this.metricRepository = metricRepository;
        this.retrievalGateway = retrievalGateway;
    }

    public List<EvidenceChunk> retrieve(String question, Map<String, Object> structuredQuery) {
        String companySymbol = (String) structuredQuery.get("companySymbol");
        List<EvidenceChunk> evidence = new ArrayList<>();
        retrievalGateway.search(companySymbol, question, 6)
                .forEach(hit -> evidence.add(toEvidence(hit)));
        if (Boolean.TRUE.equals(structuredQuery.get("requiresMetrics")) && companySymbol != null) {
            evidence.addAll(metricEvidence(companySymbol));
        }
        return evidence;
    }

    private EvidenceChunk toEvidence(RetrievalHit hit) {
        return new EvidenceChunk(
                hit.chunk().documentId(),
                hit.chunk().title(),
                hit.chunk().documentType(),
                hit.chunk().publishedAt(),
                hit.chunk().section() + " / " + hit.channel(),
                hit.chunk().text(),
                hit.score()
        );
    }

    private List<EvidenceChunk> metricEvidence(String companySymbol) {
        List<EvidenceChunk> evidence = new ArrayList<>();
        List<FinancialMetric> metrics = metricRepository.findMetrics(companySymbol);
        if (!metrics.isEmpty()) {
            String text = metrics.stream()
                    .limit(12)
                    .map(metric -> metric.fiscalYear() + " " + metric.name() + "=" + metric.value())
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("");
            evidence.add(new EvidenceChunk("metric-store-" + companySymbol, "结构化财务指标库", DocumentType.ANNUAL_REPORT, null, "指标计算结果", text, 0.95));
        }
        List<RiskSignal> signals = metricRepository.findRiskSignals(companySymbol);
        if (!signals.isEmpty()) {
            String text = signals.stream()
                    .map(signal -> signal.title() + "：" + signal.explanation())
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("");
            evidence.add(new EvidenceChunk("risk-store-" + companySymbol, "财务风险信号库", DocumentType.ANNUAL_REPORT, null, "异常检测结果", text, 0.9));
        }
        return evidence;
    }

}
