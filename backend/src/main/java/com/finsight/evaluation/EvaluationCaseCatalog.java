package com.finsight.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvaluationCaseCatalog {
    public List<EvaluationCase> cases() {
        return List.of(
                new EvaluationCase(
                        "cash-quality-600519",
                        "600519",
                        "贵州茅台最近的现金流质量怎么样？",
                        "2022-2024",
                        List.of("现金流", "结构化财务指标库"),
                        List.of("经营现金流", "以上结论来自"),
                        1200
                ),
                new EvaluationCase(
                        "risk-note-600519",
                        "600519",
                        "白酒行业有哪些需要关注的风险？",
                        "2024-2025",
                        List.of("风险提示", "库存", "批价"),
                        List.of("风险", "以上结论来自"),
                        1200
                ),
                new EvaluationCase(
                        "profitability-600519",
                        "600519",
                        "贵州茅台的盈利能力和 ROE 表现如何？",
                        "2022-2024",
                        List.of("ROE", "净资产收益率", "结构化财务指标库"),
                        List.of("净资产收益率", "以上结论来自"),
                        1200
                )
        );
    }
}
