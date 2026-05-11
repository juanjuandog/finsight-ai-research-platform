package com.finsight.domain.model;

public record Company(
        String symbol,
        String name,
        String exchange,
        String industry
) {
}

