package com.finsight.ai;

import com.finsight.domain.model.EvidenceChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FallbackAiServiceClient implements AiServiceClient {
    @Override
    public List<EvidenceChunk> rerank(String question, List<EvidenceChunk> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(EvidenceChunk::score).reversed())
                .toList();
    }

    @Override
    public String generateAnswer(String question, Map<String, Object> structuredQuery, List<EvidenceChunk> evidence) {
        String sources = evidence.stream()
                .map(chunk -> "[" + chunk.title() + " - " + chunk.section() + "]")
                .distinct()
                .collect(Collectors.joining("、"));
        String snippets = evidence.stream()
                .limit(3)
                .map(EvidenceChunk::text)
                .collect(Collectors.joining(" "));
        return "基于已检索证据，问题“" + question + "”的分析如下："
                + snippets
                + " 以上结论来自 " + sources
                + "。本系统仅做信息整理与风险提示，不构成投资建议。";
    }
}

