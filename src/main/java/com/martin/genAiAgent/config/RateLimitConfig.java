package com.martin.genAiAgent.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    
    // 存储不同用户的限流器
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();
    
    /**
     * API调用限流 - 每分钟100次请求
     */
    @Bean
    public RateLimiter apiRateLimiter() {
        return RateLimiter.of("api-rate-limiter", 
            RateLimiterConfig.custom()
                    .limitForPeriod(100)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .build());
    }
    
    /**
     * 推荐API限流 - 每分钟20次请求
     */
    @Bean
    public RateLimiter recommendationRateLimiter() {
        return RateLimiter.of("recommendation-rate-limiter", 
            RateLimiterConfig.custom()
                    .limitForPeriod(20)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .build());
    }
    
    /**
     * 登录API限流 - 每分钟5次请求
     */
    @Bean
    public RateLimiter loginRateLimiter() {
        return RateLimiter.of("login-rate-limiter", 
            RateLimiterConfig.custom()
                    .limitForPeriod(5)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .build());
    }
    
    /**
     * 获取用户的API限流器
     */
    public RateLimiter getUserApiLimiter(String userId) {
        return userLimiters.computeIfAbsent(userId, k -> 
            RateLimiter.of("user-api-" + userId,
                RateLimiterConfig.custom()
                        .limitForPeriod(50)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .build()));
    }
    
    /**
     * 获取用户的推荐限流器
     */
    public RateLimiter getUserRecommendationLimiter(String userId) {
        return userLimiters.computeIfAbsent("rec_" + userId, k -> 
            RateLimiter.of("user-rec-" + userId,
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .build()));
    }
    
    /**
     * 检查API限流
     */
    public boolean tryAcquireApiPermit(String userId) {
        RateLimiter limiter = getUserApiLimiter(userId);
        boolean acquired = limiter.acquirePermission();
        if (!acquired) {
            log.warn("用户 {} API调用频率超限", userId);
        }
        return acquired;
    }
    
    /**
     * 检查推荐限流
     */
    public boolean tryAcquireRecommendationPermit(String userId) {
        RateLimiter limiter = getUserRecommendationLimiter(userId);
        boolean acquired = limiter.acquirePermission();
        if (!acquired) {
            log.warn("用户 {} 推荐频率超限", userId);
        }
        return acquired;
    }
    
    /**
     * 获取剩余许可数
     */
    public long getAvailableApiPermits(String userId) {
        RateLimiter limiter = getUserApiLimiter(userId);
        return limiter.getMetrics().getAvailablePermissions();
    }
    
    /**
     * 获取剩余推荐许可数
     */
    public long getAvailableRecommendationPermits(String userId) {
        RateLimiter limiter = getUserRecommendationLimiter(userId);
        return limiter.getMetrics().getAvailablePermissions();
    }
}
