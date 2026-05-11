package com.finsight.rag;

import com.finsight.domain.model.DocumentType;
import com.finsight.domain.model.EvidenceChunk;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.domain.repository.MetricRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceRetriever {
    private final DocumentRepository documentRepository;
    private final MetricRepository metricRepository;

    public EvidenceRetriever(DocumentRepository documentRepository, MetricRepository metricRepository) {
        this.documentRepository = documentRepository;
        this.metricRepository = metricRepository;
    }

    public List<EvidenceChunk> retrieve(String question, Map<String, Object> structuredQuery) {
        String companySymbol = (String) structuredQuery.get("companySymbol");
        List<EvidenceChunk> evidence = new ArrayList<>();
        List<FinancialDocument> documents = documentRepository.search(companySymbol, question, 6);
        if (documents.isEmpty() && companySymbol != null) {
            documents = documentRepository.findByCompanySymbol(companySymbol).stream().limit(6).toList();
        }
        for (FinancialDocument document : documents) {
            evidence.add(toChunk(document, estimateScore(question, document)));
        }
        if (Boolean.TRUE.equals(structuredQuery.get("requiresMetrics")) && companySymbol != null) {
            evidence.addAll(metricEvidence(companySymbol));
        }
        return evidence;
    }

    private EvidenceChunk toChunk(FinancialDocument document, double score) {
        String section = document.metadata().getOrDefault("section", "正文");
        String text = document.content().length() > 180 ? document.content().substring(0, 180) : document.content();
        return new EvidenceChunk(document.id(), document.title(), document.type(), document.publishedAt(), section, text, score);
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

    private double estimateScore(String question, FinancialDocument document) {
        double score = 0.5;
        if (question != null && document.content().contains(question)) {
            score += 0.4;
        }
        if (document.type() == DocumentType.ANNUAL_REPORT || document.type() == DocumentType.RESEARCH_REPORT) {
            score += 0.2;
        }
        return score;
    }
}

