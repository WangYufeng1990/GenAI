package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {
    
    private final Map<String, List<VideoResource>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, Object> userProfileCache = new ConcurrentHashMap<>();
    
    /**
     * 缓存搜索结果
     */
    @Cacheable(value = "videoSearch", key = "#query + '_' + #specialNeeds + '_' + #childAge")
    public List<VideoResource> getCachedSearchResults(String query, String specialNeeds, String childAge) {
        String cacheKey = generateSearchKey(query, specialNeeds, childAge);
        return searchCache.get(cacheKey);
    }
    
    /**
     * 缓存搜索结果
     */
    public void cacheSearchResults(String query, String specialNeeds, String childAge, List<VideoResource> results) {
        String cacheKey = generateSearchKey(query, specialNeeds, childAge);
        searchCache.put(cacheKey, results);
        log.debug("缓存搜索结果: key={}, size={}", cacheKey, results.size());
    }
    
    /**
     * 缓存用户画像
     */
    @Cacheable(value = "userProfile", key = "#userId")
    public Object getCachedUserProfile(String userId) {
        return userProfileCache.get(userId);
    }
    
    /**
     * 缓存用户画像
     */
    public void cacheUserProfile(String userId, Object profile) {
        userProfileCache.put(userId, profile);
        log.debug("缓存用户画像: userId={}", userId);
    }
    
    /**
     * 清除用户画像缓存
     */
    @Cacheable(value = "userProfile", key = "#userId")
    public void evictUserProfile(String userId) {
        userProfileCache.remove(userId);
        log.debug("清除用户画像缓存: userId={}", userId);
    }
    
    /**
     * 清除搜索缓存
     */
    public void evictSearchCache(String query, String specialNeeds, String childAge) {
        String cacheKey = generateSearchKey(query, specialNeeds, childAge);
        searchCache.remove(cacheKey);
        log.debug("清除搜索缓存: key={}", cacheKey);
    }
    
    /**
     * 生成搜索缓存键
     */
    private String generateSearchKey(String query, String specialNeeds, String childAge) {
        return String.format("%s_%s_%s", query, specialNeeds, childAge);
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("searchCacheSize", searchCache.size());
        stats.put("userProfileCacheSize", userProfileCache.size());
        return stats;
    }
}
