package com.finsight.domain.model;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionCommand(
        @NotBlank String question,
        String companySymbol,
        String timeRange
) {
}

