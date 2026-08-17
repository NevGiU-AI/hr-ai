package com.nevgiu.hrai.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class LoginThrottleService {
    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            if attempts >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
              redis.call('DEL', KEYS[1])
              return tonumber(ARGV[3])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LoginThrottleProperties properties;

    public LoginThrottleService(StringRedisTemplate redis, LoginThrottleProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public ThrottleDecision check(String email, String clientIp) {
        long accountSeconds = remainingSeconds(accountLockKey(email));
        long ipSeconds = remainingSeconds(ipLockKey(clientIp));
        return new ThrottleDecision(Math.max(accountSeconds, ipSeconds));
    }

    public ThrottleDecision recordFailure(String email, String clientIp) {
        long accountSeconds = record(accountAttemptsKey(email), accountLockKey(email),
                properties.accountFailureLimit());
        long ipSeconds = record(ipAttemptsKey(clientIp), ipLockKey(clientIp), properties.ipFailureLimit());
        return new ThrottleDecision(Math.max(accountSeconds, ipSeconds));
    }

    public void recordSuccess(String email) {
        redis.delete(accountAttemptsKey(email));
        redis.delete(accountLockKey(email));
    }

    public boolean isAccountLocked(String email) {
        return remainingSeconds(accountLockKey(email)) > 0;
    }

    public long accountLockRemainingSeconds(String email) {
        return remainingSeconds(accountLockKey(email));
    }

    public void unlockAccount(String email) {
        redis.delete(List.of(accountAttemptsKey(email), accountLockKey(email)));
    }

    private long record(String attemptsKey, String lockKey, int failureLimit) {
        Long result = redis.execute(RECORD_FAILURE, List.of(attemptsKey, lockKey),
                Long.toString(properties.failureWindow().toSeconds()), Integer.toString(failureLimit),
                Long.toString(properties.lockDuration().toSeconds()));
        return result == null ? 0 : result;
    }

    private long remainingSeconds(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null || ttl <= 0 ? 0 : ttl;
    }

    private String accountAttemptsKey(String email) {
        return key("account", "attempts", normalizeEmail(email));
    }

    private String accountLockKey(String email) {
        return key("account", "lock", normalizeEmail(email));
    }

    private String ipAttemptsKey(String clientIp) {
        return key("ip", "attempts", normalizeIp(clientIp));
    }

    private String ipLockKey(String clientIp) {
        return key("ip", "lock", normalizeIp(clientIp));
    }

    private String key(String subject, String state, String value) {
        return properties.namespace() + ':' + subject + ':' + state + ':' + sha256(value);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ThrottleDecision(long retryAfterSeconds) {
        public boolean blocked() {
            return retryAfterSeconds > 0;
        }
    }
}
