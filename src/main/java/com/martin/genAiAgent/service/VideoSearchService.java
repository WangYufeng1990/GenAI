package com.martin.genAiAgent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.martin.genAiAgent.model.VideoResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.*;

/**
 * 视频搜索服务 - 集成多个视频搜索API
 */
@Service
public class VideoSearchService {
    
    private final YouTubeApiService youtubeApiService;
    private final KhanAcademyApiService khanAcademyApiService;
    private final PBSKidsApiService pbsKidsApiService;
    private final ApiCacheService apiCacheService;
    
    @Autowired
    public VideoSearchService(YouTubeApiService youtubeApiService,
                           KhanAcademyApiService khanAcademyApiService,
                           PBSKidsApiService pbsKidsApiService,
                           ApiCacheService apiCacheService) {
        this.youtubeApiService = youtubeApiService;
        this.khanAcademyApiService = khanAcademyApiService;
        this.pbsKidsApiService = pbsKidsApiService;
        this.apiCacheService = apiCacheService;
    }
    
    /**
     * 多平台搜索教育视频
     */
    public Flux<VideoResource> searchEducationalVideos(String query, String specialNeeds, String ageRange) {
        return Flux.merge(
            searchYouTube(query, specialNeeds, ageRange),
            searchKhanAcademy(query, specialNeeds, ageRange),
            searchPBSKids(query, specialNeeds, ageRange)
        ).distinct(video -> video.getId()); // 去重
    }
    
    /**
     * YouTube Data API 搜索
     */
    private Flux<VideoResource> searchYouTube(String query, String specialNeeds, String ageRange) {
        return apiCacheService.cacheYouTubeResults(query, specialNeeds, ageRange,
            youtubeApiService.searchEducationalVideos(query, specialNeeds, ageRange, 10)
        ).doOnNext(video -> {
            System.out.println("YouTube result: " + video.getTitle());
        });
    }
    
    /**
     * Khan Academy API 搜索
     */
    private Flux<VideoResource> searchKhanAcademy(String query, String specialNeeds, String ageRange) {
        return apiCacheService.cacheKhanAcademyResults(query, specialNeeds, ageRange,
            khanAcademyApiService.searchEducationalContent(query, specialNeeds, ageRange, 8)
        ).doOnNext(video -> {
            System.out.println("Khan Academy result: " + video.getTitle());
        });
    }
    
    /**
     * PBS Kids API 搜索
     */
    private Flux<VideoResource> searchPBSKids(String query, String specialNeeds, String ageRange) {
        return apiCacheService.cachePBSKidsResults(query, specialNeeds, ageRange,
            pbsKidsApiService.searchEducationalContent(query, specialNeeds, ageRange, 8)
        ).doOnNext(video -> {
            System.out.println("PBS Kids result: " + video.getTitle());
        });
    }
    
    /**
     * 获取搜索统计信息
     */
    public Mono<Map<String, Object>> getSearchStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 缓存统计
            stats.put("cacheStats", apiCacheService.getCacheStats());
            
            // API 状态
            Map<String, Object> apiStatus = new HashMap<>();
            apiStatus.put("youtube", "active");
            apiStatus.put("khan-academy", "active");
            apiStatus.put("pbs-kids", "active");
            stats.put("apiStatus", apiStatus);
            
            return stats;
        });
    }
    
    /**
     * 预热缓存
     */
    public Mono<Void> warmupCache() {
        return apiCacheService.warmupCache()
                .doOnSuccess(v -> System.out.println("Cache warmup completed"))
                .doOnError(e -> System.err.println("Cache warmup failed: " + e.getMessage()));
    }
    
    /**
     * 清理缓存
     */
    public Mono<Void> clearCache() {
        return Mono.fromRunnable(() -> {
            System.out.println("Clearing all caches...");
            // 这里可以添加具体的缓存清理逻辑
        });
    }
}
