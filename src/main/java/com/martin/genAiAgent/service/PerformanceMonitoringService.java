package com.martin.genAiAgent.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringService {
    
    private final Counter recommendationCounter;
    private final Counter userLoginCounter;
    private final Counter apiRequestCounter;
    private final Timer recommendationTimer;
    
    // 自定义计数器
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong databaseQueries = new AtomicLong(0);
    private final AtomicLong aiRequests = new AtomicLong(0);
    
    /**
     * 记录推荐生成
     */
    public void recordRecommendationGenerated(String userId, int count) {
        recommendationCounter.increment();
        log.info("推荐生成: userId={}, count={}", userId, count);
    }
    
    /**
     * 记录用户登录
     */
    public void recordUserLogin(String username) {
        userLoginCounter.increment();
        log.info("用户登录: username={}", username);
    }
    
    /**
     * 记录API请求
     */
    public void recordApiRequest(String endpoint, String method) {
        apiRequestCounter.increment();
        log.debug("API请求: {} {}", method, endpoint);
    }
    
    /**
     * 记录推荐生成时间
     */
    public void recordRecommendationTime(Duration duration) {
        recommendationTimer.record(duration);
        log.debug("推荐生成时间: {}ms", duration.toMillis());
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit(String cacheType) {
        cacheHits.incrementAndGet();
        log.debug("缓存命中: type={}", cacheType);
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss(String cacheType) {
        cacheMisses.incrementAndGet();
        log.debug("缓存未命中: type={}", cacheType);
    }
    
    /**
     * 记录数据库查询
     */
    public void recordDatabaseQuery(String queryType) {
        databaseQueries.incrementAndGet();
        log.debug("数据库查询: type={}", queryType);
    }
    
    /**
     * 记录AI请求
     */
    public void recordAiRequest(String model) {
        aiRequests.incrementAndGet();
        log.debug("AI请求: model={}", model);
    }
    
    /**
     * 获取性能统计
     */
    public PerformanceStats getPerformanceStats() {
        PerformanceStats stats = new PerformanceStats();
        stats.setCacheHits(cacheHits.get());
        stats.setCacheMisses(cacheMisses.get());
        stats.setDatabaseQueries(databaseQueries.get());
        stats.setAiRequests(aiRequests.get());
        
        long totalCacheRequests = cacheHits.get() + cacheMisses.get();
        if (totalCacheRequests > 0) {
            stats.setCacheHitRate((double) cacheHits.get() / totalCacheRequests * 100);
        }
        
        return stats;
    }
    
    /**
     * 性能统计数据类
     */
    public static class PerformanceStats {
        private long cacheHits;
        private long cacheMisses;
        private long databaseQueries;
        private long aiRequests;
        private double cacheHitRate;
        
        // Getters and Setters
        public long getCacheHits() { return cacheHits; }
        public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }
        
        public long getCacheMisses() { return cacheMisses; }
        public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }
        
        public long getDatabaseQueries() { return databaseQueries; }
        public void setDatabaseQueries(long databaseQueries) { this.databaseQueries = databaseQueries; }
        
        public long getAiRequests() { return aiRequests; }
        public void setAiRequests(long aiRequests) { this.aiRequests = aiRequests; }
        
        public double getCacheHitRate() { return cacheHitRate; }
        public void setCacheHitRate(double cacheHitRate) { this.cacheHitRate = cacheHitRate; }
    }
}
