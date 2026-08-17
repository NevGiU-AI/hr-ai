package com.nevgiu.hrai.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginThrottleServiceTest {
    @Mock StringRedisTemplate redis;

    @Test
    void locksWhenEitherAccountOrIpReachesItsLimit() {
        LoginThrottleService service = service();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(900L, 0L);

        LoginThrottleService.ThrottleDecision decision =
                service.recordFailure(" User@Example.com ", "203.0.113.10");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.retryAfterSeconds()).isEqualTo(900);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void hashesSensitiveIdentifiersInRedisKeys() {
        LoginThrottleService service = service();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);

        service.recordFailure("person@example.com", "203.0.113.10");

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), keys.capture(), any(), any(), any());
        assertThat(keys.getAllValues().toString())
                .doesNotContain("person@example.com")
                .doesNotContain("203.0.113.10")
                .contains("test:login");
    }

    @Test
    void successfulLoginClearsOnlyTheAccountsAttemptsAndLock() {
        LoginThrottleService service = service();

        service.recordSuccess("person@example.com");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).delete(key.capture());
        assertThat(key.getAllValues()).allMatch(value -> value.contains(":account:"));
    }

    @Test
    void reportsTheRemainingAccountLockTime() {
        LoginThrottleService service = service();
        when(redis.getExpire(any())).thenReturn(42L);

        assertThat(service.accountLockRemainingSeconds("person@example.com")).isEqualTo(42);
        assertThat(service.isAccountLocked("person@example.com")).isTrue();
    }

    private LoginThrottleService service() {
        return new LoginThrottleService(redis, new LoginThrottleProperties(
                "test:login", 5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15)));
    }
}
