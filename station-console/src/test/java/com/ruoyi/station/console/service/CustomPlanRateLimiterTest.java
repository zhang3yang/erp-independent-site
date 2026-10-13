package com.ruoyi.station.console.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.ruoyi.station.console.config.StationProperties;

/**
 * 定制方案限流：三层计数、<b>Redis 挂了要拒绝</b>（和询盘限流相反）。
 */
@ExtendWith(MockitoExtension.class)
class CustomPlanRateLimiterTest
{
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Spy StationProperties properties = new StationProperties();
    @InjectMocks CustomPlanRateLimiter limiter;

    @BeforeEach
    void setUp()
    {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void allowsUnderLimit()
    {
        when(ops.increment(anyString())).thenReturn(2L);
        assertTrue(limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    void rejectsWhenAnyLayerExceeded()
    {
        // 小时 10、天 30、全站 500；计数 11 只超了小时那层，也要拒
        when(ops.increment(anyString())).thenReturn(11L);
        assertFalse(limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    void siteLayerCountsAcrossIps()
    {
        when(ops.increment(anyString())).thenAnswer(inv ->
                ((String) inv.getArgument(0)).contains(":site:") ? 501L : 1L);
        assertFalse(limiter.tryAcquire("5.6.7.8"));
    }

    @Test
    void rejectsWhenRedisIsDown()
    {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertFalse(limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    void rejectsWhenRedisReturnsNull()
    {
        when(ops.increment(anyString())).thenReturn(null);
        assertFalse(limiter.tryAcquire("1.2.3.4"));
    }
}
