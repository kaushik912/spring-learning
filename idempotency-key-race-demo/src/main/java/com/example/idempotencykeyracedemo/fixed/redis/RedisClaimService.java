package com.example.idempotencykeyracedemo.fixed.redis;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * FIX: Redis's SET key value NX ("set if not exists") is a single atomic
 * command on the Redis server. Two concurrent claims for the same key both
 * reach Redis, but the server serializes them — only the first SETNX
 * returns true, the second always returns false.
 */
@Service
public class RedisClaimService {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    public RedisClaimService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return true if this call won the race and claimed the key, false if
     *         another request already claimed it first.
     */
    public boolean tryClaim(String idempotencyKey) {
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + idempotencyKey, "PROCESSED", Duration.ofMinutes(10));
        return Boolean.TRUE.equals(claimed);
    }
}
