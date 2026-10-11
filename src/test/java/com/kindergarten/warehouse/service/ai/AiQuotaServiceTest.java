package com.kindergarten.warehouse.service.ai;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiQuotaServiceTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AiQuotaService quota = new AiQuotaService(redis, 2);

    {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void countsDownThenRefuses() {
        when(ops.increment(anyString())).thenReturn(1L, 2L, 3L);
        assertThat(quota.consume(7L)).isEqualTo(1);
        assertThat(quota.consume(7L)).isEqualTo(0);
        assertThatThrownBy(() -> quota.consume(7L)).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
    }

    @Test
    void redisFailureFailsClosed() {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("connection refused"));
        assertThatThrownBy(() -> quota.consume(7L)).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
    }
}
