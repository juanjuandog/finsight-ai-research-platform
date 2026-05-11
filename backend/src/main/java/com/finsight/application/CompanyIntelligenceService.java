package com.finsight.application;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.CompanyEvent;
import com.finsight.domain.model.EntityType;
import com.finsight.domain.model.EventType;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.KnowledgeEntity;
import com.finsight.domain.model.KnowledgeRelation;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.CompanyEventRepository;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.domain.repository.KnowledgeGraphRepository;
import com.finsight.domain.repository.MetricRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CompanyIntelligenceService {
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final MetricRepository metricRepository;
    private final CompanyEventRepository eventRepository;
    private final KnowledgeGraphRepository graphRepository;

    public CompanyIntelligenceService(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            MetricRepository metricRepository,
            CompanyEventRepository eventRepository,
            KnowledgeGraphRepository graphRepository
    ) {
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
        this.metricRepository = metricRepository;
        this.eventRepository = eventRepository;
        this.graphRepository = graphRepository;
    }

    public BuildResult rebuild(String companySymbol) {
        Company company = companyRepository.findBySymbol(companySymbol)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companySymbol));
        List<FinancialDocument> documents = documentRepository.findByCompanySymbol(companySymbol);
        List<FinancialMetric> metrics = metricRepository.findMetrics(companySymbol);
        List<RiskSignal> risks = metricRepository.findRiskSignals(companySymbol);

        List<CompanyEvent> events = buildEvents(company, documents, metrics, risks);
        eventRepository.replaceCompanyEvents(companySymbol, events);

        GraphDraft graph = buildGraph(company, documents, metrics, risks, events);
        graphRepository.replaceCompanyGraph(companySymbol, graph.entities(), graph.relations());
        return new BuildResult(companySymbol, events.size(), graph.entities().size(), graph.relations().size());
    }

    public List<CompanyEvent> timeline(String companySymbol) {
        return eventRepository.findByCompanySymbol(companySymbol);
    }

    public CompanyGraph graph(String companySymbol) {
        return new CompanyGraph(
                graphRepository.findEntities(companySymbol),
                graphRepository.findRelations(companySymbol)
        );
    }

    private List<CompanyEvent> buildEvents(
            Company company,
            List<FinancialDocument> documents,
            List<FinancialMetric> metrics,
            List<RiskSignal> risks
    ) {
        List<CompanyEvent> events = new ArrayList<>();
        documents.stream()
                .sorted(Comparator.comparing(FinancialDocument::publishedAt))
                .forEach(document -> events.add(new CompanyEvent(
                        id(company.symbol(), "document", document.id()),
                        company.symbol(),
                        switch (document.type()) {
                            case RESEARCH_REPORT -> EventType.RESEARCH_VIEW;
                            case ANNUAL_REPORT, QUARTERLY_REPORT -> EventType.FINANCIAL_RESULT;
                            default -> EventType.INDUSTRY_CHANGE;
                        },
                        document.publishedAt(),
                        document.title(),
                        document.content().length() > 120 ? document.content().substring(0, 120) : document.content(),
                        List.of(document.id())
                )));

        metrics.stream()
                .filter(metric -> List.of("REVENUE_YOY", "NET_PROFIT_YOY", "OCF_NET_PROFIT", "ROE").contains(metric.code()))
                .forEach(metric -> events.add(new CompanyEvent(
                        id(company.symbol(), "metric", metric.fiscalYear() + metric.code()),
                        company.symbol(),
                        EventType.FINANCIAL_RESULT,
                        LocalDate.of(metric.fiscalYear().getValue(), 12, 31),
                        metric.fiscalYear() + " " + metric.name() + " = " + metric.value(),
                        metricSummary(metric),
                        List.of()
                )));

        risks.forEach(risk -> events.add(new CompanyEvent(
                id(company.symbol(), "risk", risk.id()),
                company.symbol(),
                EventType.RISK_SIGNAL,
                risk.detectedAt(),
                risk.title(),
                risk.explanation(),
                List.of()
        )));
        return events.stream()
                .sorted(Comparator.comparing(CompanyEvent::happenedAt).reversed())
                .toList();
    }

    private GraphDraft buildGraph(
            Company company,
            List<FinancialDocument> documents,
            List<FinancialMetric> metrics,
            List<RiskSignal> risks,
            List<CompanyEvent> events
    ) {
        Map<String, KnowledgeEntity> entities = new LinkedHashMap<>();
        List<KnowledgeRelation> relations = new ArrayList<>();

        KnowledgeEntity companyEntity = entity(EntityType.COMPANY, company.symbol(), company.name(), company.symbol(), Map.of(
                "exchange", company.exchange(),
                "industry", company.industry()
        ));
        KnowledgeEntity industryEntity = entity(EntityType.INDUSTRY, company.industry(), company.industry(), company.symbol(), Map.of());
        entities.put(companyEntity.id(), companyEntity);
        entities.put(industryEntity.id(), industryEntity);
        relations.add(relation(company.symbol(), companyEntity.id(), industryEntity.id(), "BELONGS_TO_INDUSTRY", null, Map.of()));

        documents.forEach(document -> {
            KnowledgeEntity documentEntity = entity(EntityType.DOCUMENT, document.id(), document.title(), company.symbol(), Map.of(
                    "documentType", document.type().name(),
                    "publishedAt", document.publishedAt().toString()
            ));
            entities.put(documentEntity.id(), documentEntity);
            relations.add(relation(company.symbol(), companyEntity.id(), documentEntity.id(), "PUBLISHED_DOCUMENT", document.id(), Map.of()));
            extractIndustryKeywords(company, document).forEach(keyword -> {
                KnowledgeEntity keywordEntity = entity(EntityType.PRODUCT, keyword, keyword, company.symbol(), Map.of("source", "document_keyword"));
                entities.putIfAbsent(keywordEntity.id(), keywordEntity);
                relations.add(relation(company.symbol(), documentEntity.id(), keywordEntity.id(), "MENTIONS_KEYWORD", document.id(), Map.of()));
            });
        });

        metrics.stream()
                .filter(metric -> List.of("ROE", "GROSS_MARGIN", "OCF_NET_PROFIT", "RECEIVABLE_GROWTH_SPREAD").contains(metric.code()))
                .forEach(metric -> {
                    KnowledgeEntity metricEntity = entity(EntityType.FINANCIAL_METRIC, metric.code(), metric.name(), company.symbol(), Map.of(
                            "latestValue", metric.value().toPlainString(),
                            "formulaVersion", metric.formulaVersion()
                    ));
                    entities.put(metricEntity.id(), metricEntity);
                    relations.add(relation(company.symbol(), companyEntity.id(), metricEntity.id(), "HAS_FINANCIAL_METRIC", null, Map.of(
                            "fiscalYear", metric.fiscalYear().toString()
                    )));
                });

        risks.forEach(risk -> {
            KnowledgeEntity riskEntity = entity(EntityType.RISK_EVENT, risk.code(), risk.title(), company.symbol(), Map.of(
                    "severity", String.valueOf(risk.severity()),
                    "detectedAt", risk.detectedAt().toString()
            ));
            entities.put(riskEntity.id(), riskEntity);
            relations.add(relation(company.symbol(), companyEntity.id(), riskEntity.id(), "HAS_RISK_SIGNAL", risk.id(), Map.of()));
        });

        events.forEach(event -> {
            KnowledgeEntity eventEntity = entity(EntityType.EVENT, event.id(), event.title(), company.symbol(), Map.of(
                    "eventType", event.type().name(),
                    "happenedAt", event.happenedAt().toString()
            ));
            entities.putIfAbsent(eventEntity.id(), eventEntity);
            relations.add(relation(company.symbol(), companyEntity.id(), eventEntity.id(), "HAS_TIMELINE_EVENT", event.id(), Map.of()));
        });

        return new GraphDraft(List.copyOf(entities.values()), relations);
    }

    private List<String> extractIndustryKeywords(Company company, FinancialDocument document) {
        List<String> keywords = new ArrayList<>();
        if (document.content().contains("渠道")) {
            keywords.add("渠道");
        }
        if (document.content().contains("库存")) {
            keywords.add("库存");
        }
        if (document.content().contains("批价")) {
            keywords.add("批价");
        }
        if (document.content().contains("现金流")) {
            keywords.add("现金流");
        }
        if (keywords.isEmpty()) {
            keywords.add(company.industry());
        }
        return keywords;
    }

    private String metricSummary(FinancialMetric metric) {
        BigDecimal value = metric.value();
        if (metric.code().endsWith("_YOY")) {
            return metric.name() + "为 " + value + "，用于观察增长趋势。";
        }
        return metric.name() + "为 " + value + "，用于刻画公司基本面状态。";
    }

    private KnowledgeEntity entity(EntityType type, String naturalKey, String name, String companySymbol, Map<String, String> properties) {
        return new KnowledgeEntity(id(companySymbol, type.name(), naturalKey), type, name, companySymbol, properties);
    }

    private KnowledgeRelation relation(
            String companySymbol,
            String source,
            String target,
            String type,
            String evidenceId,
            Map<String, String> properties
    ) {
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.put("companySymbol", companySymbol);
        return new KnowledgeRelation(
                id(companySymbol, source, target, type, evidenceId == null ? "" : evidenceId, merged.toString()),
                source,
                target,
                type,
                evidenceId,
                merged
        );
    }

    private String id(String... parts) {
        return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record GraphDraft(List<KnowledgeEntity> entities, List<KnowledgeRelation> relations) {
    }

    public record BuildResult(String companySymbol, int eventCount, int entityCount, int relationCount) {
    }

    public record CompanyGraph(List<KnowledgeEntity> entities, List<KnowledgeRelation> relations) {
    }
}
