package com.finsight.workflow;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RedisBackedWorkflowLeaseService implements WorkflowLeaseService {
    private static final String ACQUIRE_LUA = """
            local leaseKey = KEYS[1]
            local fenceKey = KEYS[2]
            local owner = ARGV[1]
            local ttlMillis = tonumber(ARGV[2])
            if redis.call('exists', leaseKey) == 0 then
                local token = redis.call('incr', fenceKey)
                redis.call('psetex', leaseKey, ttlMillis, owner .. ':' .. token)
                return token
            end
            return nil
            """;
    private static final String RELEASE_LUA = """
            local leaseKey = KEYS[1]
            local expected = ARGV[1]
            if redis.call('get', leaseKey) == expected then
                return redis.call('del', leaseKey)
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final String ownerPrefix;
    private final ConcurrentHashMap<String, WorkflowLease> localLeases = new ConcurrentHashMap<>();
    private final AtomicLong localFence = new AtomicLong();

    public RedisBackedWorkflowLeaseService(
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${finsight.workflow.lease-owner:}") String configuredOwner
    ) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.ownerPrefix = configuredOwner == null || configuredOwner.isBlank()
                ? ManagementFactory.getRuntimeMXBean().getName()
                : configuredOwner;
    }

    @Override
    public Optional<WorkflowLease> tryAcquire(String key, Duration ttl) {
        String owner = ownerPrefix + ":" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(ttl);
        if (redisTemplate != null) {
            try {
                Long token = redisTemplate.execute(
                        new DefaultRedisScript<>(ACQUIRE_LUA, Long.class),
                        List.of(redisKey(key), redisFenceKey(key)),
                        owner,
                        String.valueOf(ttl.toMillis())
                );
                return token == null ? Optional.empty() : Optional.of(new WorkflowLease(key, owner, token, expiresAt));
            } catch (RuntimeException ignored) {
                // Fall back to local single-flight when Redis is not reachable in local/dev mode.
            }
        }
        WorkflowLease lease = new WorkflowLease(key, owner, localFence.incrementAndGet(), expiresAt);
        WorkflowLease existing = localLeases.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(Instant.now())) {
                return lease;
            }
            return current;
        });
        return lease.equals(existing) ? Optional.of(lease) : Optional.empty();
    }

    @Override
    public void release(WorkflowLease lease) {
        if (redisTemplate != null) {
            try {
                redisTemplate.execute(
                        new DefaultRedisScript<>(RELEASE_LUA, Long.class),
                        List.of(redisKey(lease.key())),
                        lease.owner() + ":" + lease.fencingToken()
                );
                return;
            } catch (RuntimeException ignored) {
                // Local fallback cleanup below.
            }
        }
        localLeases.remove(lease.key(), lease);
    }

    private String redisKey(String key) {
        return "finsight:workflow:lease:" + key;
    }

    private String redisFenceKey(String key) {
        return "finsight:workflow:fence:" + key;
    }
}
