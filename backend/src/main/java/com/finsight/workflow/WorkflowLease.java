package com.finsight.workflow;

import java.time.Instant;

public record WorkflowLease(
        String key,
        String owner,
        long fencingToken,
        Instant expiresAt
) {
}
