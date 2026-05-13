package com.finsight.domain.model;

import java.time.Instant;

public record UserWatchlistItem(
        String userId,
        Company company,
        Instant createdAt
) {
}
