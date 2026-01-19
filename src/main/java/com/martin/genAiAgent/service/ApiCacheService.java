package com.martin.genAiAgent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.martin.genAiAgent.model.VideoResource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * API 缓存和限流服务
 */
@Service
public class ApiCacheService {
    
    private final CacheManager cacheManager;
    private final Map<String, ApiRateLimit> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastApiCall = new ConcurrentHashMap<>();
    
    @Autowired
    public ApiCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * 缓存 YouTube 搜索结果
     */
    @Cacheable(value = "youtube-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> cacheYouTubeResults(String query, String specialNeeds, String ageRange, 
                                              Flux<VideoResource> results) {
        return checkRateLimit("youtube", () -> results)
                .doOnNext(video -> {
                    // 记录缓存命中
                    System.out.println("YouTube cache hit for: " + query);
                });
    }
    
    /**
     * 缓存 Khan Academy 搜索结果
     */
    @Cacheable(value = "khan-academy-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> cacheKhanAcademyResults(String query, String specialNeeds, String ageRange, 
                                                      Flux<VideoResource> results) {
        return checkRateLimit("khan-academy", () -> results)
                .doOnNext(video -> {
                    System.out.println("Khan Academy cache hit for: " + query);
                });
    }
    
    /**
     * 缓存 PBS Kids 搜索结果
     */
    @Cacheable(value = "pbs-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> cachePBSKidsResults(String query, String specialNeeds, String ageRange, 
                                                Flux<VideoResource> results) {
        return checkRateLimit("pbs", () -> results)
                .doOnNext(video -> {
                    System.out.println("PBS Kids cache hit for: " + query);
                });
    }
    
    /**
     * 检查 API 调用频率限制
     */
    private <T> Flux<T> checkRateLimit(String apiName, java.util.function.Supplier<Flux<T>> apiCall) {
        ApiRateLimit rateLimit = rateLimits.computeIfAbsent(apiName, 
            k -> new ApiRateLimit(60, 1000)); // 每分钟60次，每小时1000次
        
        LocalDateTime now = LocalDateTime.now();
        
        // 检查是否超过限制
        if (rateLimit.isExceeded(now)) {
            System.err.println("API rate limit exceeded for: " + apiName);
            return Flux.error(new RuntimeException("API rate limit exceeded for " + apiName));
        }
        
        // 更新调用记录
        rateLimit.recordCall(now);
        lastApiCall.put(apiName, now);
        
        return apiCall.get();
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 缓存管理器统计
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    Map<String, Object> cacheInfo = new HashMap<>();
                    cacheInfo.put("name", cacheName);
                    cacheInfo.put("nativeCache", cache.getNativeCache().getClass().getSimpleName());
                    stats.put("cache_" + cacheName, cacheInfo);
                }
            });
        }
        
        // 限流统计
        Map<String, Object> rateLimitStats = new HashMap<>();
        rateLimits.forEach((api, limit) -> {
            Map<String, Object> apiStats = new HashMap<>();
            apiStats.put("callsPerMinute", limit.getCallsPerMinute());
            apiStats.put("callsPerHour", limit.getCallsPerHour());
            apiStats.put("remainingPerMinute", limit.getRemainingPerMinute());
            apiStats.put("remainingPerHour", limit.getRemainingPerHour());
            rateLimitStats.put(api, apiStats);
        });
        stats.put("rateLimits", rateLimitStats);
        
        // 最后调用时间
        stats.put("lastApiCalls", new HashMap<>(lastApiCall));
        
        return stats;
    }
    
    /**
     * 清理过期缓存
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    @CacheEvict(value = {"youtube-cache", "khan-academy-cache", "pbs-cache"}, allEntries = true)
    public void clearExpiredCache() {
        System.out.println("Clearing expired API cache...");
        
        // 重置限流计数器
        LocalDateTime now = LocalDateTime.now();
        rateLimits.values().forEach(limit -> limit.resetIfExpired(now));
    }
    
    /**
     * 预热缓存
     */
    public Mono<Void> warmupCache() {
        List<String> commonQueries = Arrays.asList(
            "颜色学习", "数字学习", "字母学习", "形状识别", 
            "社交技能", "情绪识别", "基础数学", "科学探索"
        );
        
        List<String> specialNeeds = Arrays.asList(
            "自闭症", "多动症", "学习障碍", "感觉统合障碍"
        );
        
        List<String> ageRanges = Arrays.asList(
            "3-5岁", "4-6岁", "5-7岁", "6-8岁"
        );
        
        // 异步预热缓存
        return Flux.fromIterable(commonQueries)
                .flatMap(query -> 
                    Flux.fromIterable(specialNeeds)
                        .flatMap(needs -> 
                            Flux.fromIterable(ageRanges)
                                .flatMap(age -> {
                                    // 这里可以调用实际的 API 服务来预热缓存
                                    System.out.println("Warming up cache for: " + query + " - " + needs + " - " + age);
                                    return Mono.empty();
                                })
                        )
                )
                .then();
    }
    
    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate(String cacheName) {
        // 这里可以实现具体的缓存命中率计算逻辑
        // 简化实现，返回模拟数据
        return 0.85; // 85% 命中率
    }
    
    /**
     * 智能缓存策略
     */
    public boolean shouldCache(String query, String specialNeeds, String ageRange) {
        // 根据查询复杂度决定是否缓存
        int complexity = query.length() + specialNeeds.length() + ageRange.length();
        
        // 复杂查询优先缓存
        if (complexity > 20) return true;
        
        // 常见关键词优先缓存
        List<String> commonKeywords = Arrays.asList("颜色", "数字", "字母", "形状", "社交", "情绪");
        boolean hasCommonKeyword = commonKeywords.stream()
            .anyMatch(keyword -> query.contains(keyword) || specialNeeds.contains(keyword));
        
        return hasCommonKeyword;
    }
    
    /**
     * API 限流管理类
     */
    private static class ApiRateLimit {
        private final int maxPerMinute;
        private final int maxPerHour;
        private final Queue<LocalDateTime> minuteCalls = new LinkedList<>();
        private final Queue<LocalDateTime> hourCalls = new LinkedList<>();
        
        public ApiRateLimit(int maxPerMinute, int maxPerHour) {
            this.maxPerMinute = maxPerMinute;
            this.maxPerHour = maxPerHour;
        }
        
        public synchronized boolean isExceeded(LocalDateTime now) {
            // 清理过期记录
            cleanupOldCalls(now);
            
            return minuteCalls.size() >= maxPerMinute || hourCalls.size() >= maxPerHour;
        }
        
        public synchronized void recordCall(LocalDateTime now) {
            minuteCalls.offer(now);
            hourCalls.offer(now);
        }
        
        private void cleanupOldCalls(LocalDateTime now) {
            // 清理超过1分钟的记录
            while (!minuteCalls.isEmpty() && 
                   Duration.between(minuteCalls.peek(), now).toMinutes() >= 1) {
                minuteCalls.poll();
            }
            
            // 清理超过1小时的记录
            while (!hourCalls.isEmpty() && 
                   Duration.between(hourCalls.peek(), now).toHours() >= 1) {
                hourCalls.poll();
            }
        }
        
        public synchronized void resetIfExpired(LocalDateTime now) {
            cleanupOldCalls(now);
        }
        
        public int getCallsPerMinute() {
            return minuteCalls.size();
        }
        
        public int getCallsPerHour() {
            return hourCalls.size();
        }
        
        public int getRemainingPerMinute() {
            return Math.max(0, maxPerMinute - minuteCalls.size());
        }
        
        public int getRemainingPerHour() {
            return Math.max(0, maxPerHour - hourCalls.size());
        }
    }
    
    /**
     * 缓存键生成器
     */
    public static class CacheKeyGenerator {
        public static String generateKey(String api, String query, String specialNeeds, String ageRange) {
            return String.format("%s:%s:%s:%s", api, query, specialNeeds, ageRange);
        }
        
        public static String generateKey(String api, Map<String, Object> params) {
            return api + ":" + params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(":"));
        }
    }
    
    /**
     * 缓存策略枚举
     */
    public enum CacheStrategy {
        LRU,      // 最近最少使用
        LFU,      // 最少使用频率
        TTL,       // 生存时间
        ADAPTIVE   // 自适应策略
    }
    
    /**
     * 获取推荐缓存策略
     */
    public CacheStrategy getRecommendedCacheStrategy(String api) {
        switch (api) {
            case "youtube":
                return CacheStrategy.TTL; // YouTube 数据变化较快，使用TTL
            case "khan-academy":
                return CacheStrategy.LFU; // Khan Academy 内容相对稳定
            case "pbs":
                return CacheStrategy.LRU; // PBS Kids 内容更新频繁
            default:
                return CacheStrategy.ADAPTIVE;
        }
    }
}
