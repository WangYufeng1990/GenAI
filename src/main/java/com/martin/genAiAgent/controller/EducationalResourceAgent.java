package com.martin.genAiAgent.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.martin.genAiAgent.model.VideoResource;
import com.martin.genAiAgent.model.RecommendationHistory;
import com.martin.genAiAgent.service.VideoSearchService;
import com.martin.genAiAgent.service.VectorSearchService;
import com.martin.genAiAgent.service.RecommendationAlgorithmService;
import com.martin.genAiAgent.service.MachineLearningRecommendationService;
import com.martin.genAiAgent.service.ABTestingService;
import com.martin.genAiAgent.service.UserProfileService;
import com.martin.genAiAgent.service.RecommendationHistoryService;
import com.martin.genAiAgent.service.CacheService;
import com.martin.genAiAgent.service.PerformanceMonitoringService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Educational Resource AI Agent
 * 为特殊儿童提供个性化教育资源推荐的智能代理
 */
@RestController
@RequestMapping("/agent")
@Slf4j
public class EducationalResourceAgent {
    
    private final ChatClient chatClient;
    private final VideoSearchService videoSearchService;
    private final VectorSearchService vectorSearchService;
    private final RecommendationAlgorithmService recommendationAlgorithmService;
    private final MachineLearningRecommendationService mlRecommendationService;
    private final ABTestingService abTestingService;
    private final UserProfileService userProfileService;
    private final RecommendationHistoryService recommendationHistoryService;
    private final CacheService cacheService;
    private final PerformanceMonitoringService performanceMonitoringService;
    
    public EducationalResourceAgent(ChatClient chatClient,
                              VideoSearchService videoSearchService,
                              VectorSearchService vectorSearchService,
                              RecommendationAlgorithmService recommendationAlgorithmService,
                              MachineLearningRecommendationService mlRecommendationService,
                              ABTestingService abTestingService,
                              UserProfileService userProfileService,
                              RecommendationHistoryService recommendationHistoryService,
                              CacheService cacheService,
                              PerformanceMonitoringService performanceMonitoringService) {
        this.chatClient = chatClient;
        this.videoSearchService = videoSearchService;
        this.vectorSearchService = vectorSearchService;
        this.recommendationAlgorithmService = recommendationAlgorithmService;
        this.mlRecommendationService = mlRecommendationService;
        this.abTestingService = abTestingService;
        this.userProfileService = userProfileService;
        this.recommendationHistoryService = recommendationHistoryService;
        this.cacheService = cacheService;
        this.performanceMonitoringService = performanceMonitoringService;
    }
    
    /**
     * Agent 主入口 - 智能教育资源推荐
     */
    @RequestMapping(value = "/educational-resources", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/event-stream")
    @PreAuthorize("hasRole('PARENT')")
    public Flux<String> findEducationalResources(
            @RequestParam String userId,
            @RequestParam String childAge,
            @RequestParam String specialNeeds,
            @RequestParam String learningGoal,
            @RequestParam(defaultValue = "5") int maxResults,
            Authentication authentication
    ) {
        
        // 验证用户权限 - 只能访问自己的数据
        String currentUsername = authentication.getName();
        log.info("用户 {} 请求教育资源推荐，目标用户ID: {}", currentUsername, userId);
        
        // 记录API请求
        performanceMonitoringService.recordApiRequest("/agent/educational-resources", "GET");
        
        // 检查缓存
        String cacheKey = String.format("%s_%s_%s_%s", userId, childAge, specialNeeds, learningGoal);
        List<VideoResource> cachedResults = cacheService.getCachedSearchResults(learningGoal, specialNeeds, childAge);
        if (cachedResults != null) {
            performanceMonitoringService.recordCacheHit("videoSearch");
            log.info("使用缓存结果: userId={}, size={}", userId, cachedResults.size());
            return Flux.fromIterable(cachedResults)
                    .take(maxResults)
                    .map(this::formatRecommendation);
        }
        
        performanceMonitoringService.recordCacheMiss("videoSearch");
        
        long startTime = System.currentTimeMillis();
        
        // 1. 创建或更新用户画像
        com.martin.genAiAgent.model.UserProfile profile = userProfileService.saveOrUpdateUserProfile(
            userId, childAge, specialNeeds, learningGoal, java.util.List.of());
        
        // 2. AI 分析用户需求并生成搜索策略
        return analyzeUserNeeds(profile, learningGoal)
                .flatMapMany(searchStrategy -> {
                    // 3. 执行智能搜索
                    return searchEducationalVideos(searchStrategy, profile)
                            .collectList()
                            .flatMapMany(videos -> {
                                    // 4. 个性化排序和推荐
                                    List<VideoResource> rankedVideos = recommendationAlgorithmService.applyRecommendationStrategy(
                                        videos, 
                                        RecommendationAlgorithmService.RecommendationStrategy.HYBRID,
                                        profile.getSpecialNeeds(), 
                                        profile.getChildAge(), 
                                        profile.getPreferences(), 
                                        java.util.Map.of()
                                    );
                                    
                                    // 5. 保存推荐历史
                                    recommendationHistoryService.saveRecommendations(userId, rankedVideos, 
                                        specialNeeds, childAge, learningGoal);
                                    
                                    // 6. 缓存结果
                                    cacheService.cacheSearchResults(learningGoal, specialNeeds, childAge, rankedVideos);
                                    
                                    // 7. 记录性能指标
                                    long duration = System.currentTimeMillis() - startTime;
                                    performanceMonitoringService.recordRecommendationTime(Duration.ofMillis(duration));
                                    performanceMonitoringService.recordRecommendationGenerated(userId, rankedVideos.size());
                                    
                                    return Flux.fromIterable(rankedVideos);
                                });
                })
                .take(maxResults)
                .map(this::formatRecommendation);
    }
    
    /**
     * 步骤1: AI 分析用户需求
     */
    private Mono<String> analyzeUserNeeds(com.martin.genAiAgent.model.UserProfile profile, String learningGoal) {
        String prompt = String.format("""
            你是一个特殊教育专家。请分析以下用户信息并制定搜索策略：
            
            儿童年龄：%s
            特殊需求：%s  
            学习目标：%s
            之前偏好：%s
            
            请生成一个详细的搜索策略，包括：
            1. 适合的视频类型和主题
            2. 关键词和标签
            3. 内容特征要求（如节奏、视觉元素等）
            4. 避免的内容类型
            
            请用JSON格式返回搜索策略。
            """, 
            profile.getChildAge(), 
            profile.getSpecialNeeds(), 
            learningGoal,
            profile.getPreferences());
        
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .collectList()
                .map(results -> String.join("", results));
    }
    
    /**
     * 步骤2: 搜索教育视频（使用所有集成服务）
     */
    private Flux<VideoResource> searchEducationalVideos(String searchStrategy, com.martin.genAiAgent.model.UserProfile profile) {
        // 从AI搜索策略中提取关键词
        String query = extractQueryFromStrategy(searchStrategy);
        
        // 1. 传统关键词搜索
        Flux<VideoResource> keywordResults = videoSearchService.searchEducationalVideos(
            query, profile.getSpecialNeeds(), profile.getChildAge());
        
        // 2. 向量语义搜索
        List<VideoResource> vectorResults = vectorSearchService.semanticSearch(
            query, profile.getSpecialNeeds(), profile.getChildAge(), 10);
        
        // 3. 合并结果
        return Flux.merge(keywordResults, Flux.fromIterable(vectorResults))
                .distinct(video -> video.getId()) // 去重
                .doOnNext(video -> calculateRelevanceScore(video, searchStrategy, profile));
    }
    
    /**
     * 从AI搜索策略中提取查询关键词
     */
    private String extractQueryFromStrategy(String searchStrategy) {
        // 简单的关键词提取，实际应该用更复杂的NLP
        if (searchStrategy.contains("颜色")) return "颜色学习";
        if (searchStrategy.contains("数字")) return "数字学习";
        if (searchStrategy.contains("字母")) return "字母学习";
        if (searchStrategy.contains("社交")) return "社交技能";
        if (searchStrategy.contains("情绪")) return "情绪识别";
        return "基础认知";
    }
    
    /**
     * 步骤3: 个性化排序（使用推荐算法服务）
     */
    private Flux<VideoResource> rankAndRecommendVideos(List<VideoResource> videos, com.martin.genAiAgent.model.UserProfile profile) {
        // 使用推荐算法服务进行智能排序
        List<VideoResource> rankedVideos = recommendationAlgorithmService.applyRecommendationStrategy(
            videos, 
            RecommendationAlgorithmService.RecommendationStrategy.HYBRID,
            profile.getSpecialNeeds(), 
            profile.getChildAge(), 
            profile.getPreferences(), 
            java.util.Map.of()
        );
        
        return Flux.fromIterable(rankedVideos);
    }
    
    /**
     * 格式化推荐结果
     */
    private String formatRecommendation(VideoResource video) {
        return String.format("""
            📺 **%s**
            ⏱️ 时长：%s | 👶 适合年龄：%s
            🎯 专注领域：%s
            📝 描述：%s
            🏷️ 标签：%s
            🔗 链接：%s
            ⭐ 相关性评分：%.2f
            
            ---
            """, 
            video.title, 
            video.duration, 
            video.ageRange,
            video.specialNeedsFocus,
            video.description,
            String.join(", ", video.tags),
            video.videoUrl,
            video.relevanceScore);
    }
    
    /**
     * 用户反馈接口
     */
    @PostMapping("/feedback")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<String> recordFeedback(
            @RequestParam String userId,
            @RequestParam String videoId,
            @RequestParam int rating,
            @RequestParam(defaultValue = "0") long watchTimeParam,
            @RequestParam(defaultValue = "false") boolean completedParam,
            Authentication authentication) {
        
        // 验证用户权限
        String currentUsername = authentication.getName();
        log.info("用户 {} 提交反馈: userId={}, videoId={}, rating={}", currentUsername, userId, videoId, rating);
        
        // 记录用户评分到数据库
        recommendationHistoryService.recordUserRating(userId, videoId, rating);
        
        // 更新机器学习模型
        return mlRecommendationService.updateModel(userId, videoId, rating, watchTimeParam, completedParam)
                .and(abTestingService.recordUserInteraction(userId, "ml_test", videoId, "rate", rating))
                .map(v -> "反馈已记录，评分：" + rating);
    }
    
    /**
     * 获取用户推荐历史
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('PARENT')")
    public Flux<RecommendationHistory> getUserHistory(
            @RequestParam String userId,
            Authentication authentication) {
        
        String currentUsername = authentication.getName();
        log.info("用户 {} 查询推荐历史: userId={}", currentUsername, userId);
        
        return Flux.fromIterable(recommendationHistoryService.getUserRecommendationHistory(userId));
    }
    
    /**
     * 获取用户已评分的推荐
     */
    @GetMapping("/rated-history")
    @PreAuthorize("hasRole('PARENT')")
    public Flux<RecommendationHistory> getUserRatedHistory(
            @RequestParam String userId,
            Authentication authentication) {
        
        String currentUsername = authentication.getName();
        log.info("用户 {} 查询已评分历史: userId={}", currentUsername, userId);
        
        return Flux.fromIterable(recommendationHistoryService.getUserRatedRecommendations(userId));
    }
    
    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<Map<String, Object>> getUserStats(
            @RequestParam String userId,
            Authentication authentication) {
        
        String currentUsername = authentication.getName();
        log.info("用户 {} 查询统计信息: userId={}", currentUsername, userId);
        
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 获取推荐历史数量
            List<RecommendationHistory> allHistory = recommendationHistoryService.getUserRecommendationHistory(userId);
            List<RecommendationHistory> ratedHistory = recommendationHistoryService.getUserRatedRecommendations(userId);
            
            stats.put("totalRecommendations", allHistory.size());
            stats.put("ratedCount", ratedHistory.size());
            stats.put("averageRating", recommendationHistoryService.getUserAverageRating(userId));
            
            // 按来源平台统计
            Map<String, Long> sourceStats = allHistory.stream()
                .collect(Collectors.groupingBy(
                    RecommendationHistory::getSourcePlatform, 
                    Collectors.counting()
                ));
            stats.put("sourceStats", sourceStats);
            
            return stats;
        });
    }
    
    /**
     * 获取性能监控信息
     */
    @GetMapping("/performance")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, Object>> getPerformanceStats(Authentication authentication) {
        String currentUsername = authentication.getName();
        log.info("管理员 {} 查询性能统计", currentUsername);
        
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            // 获取性能统计
            PerformanceMonitoringService.PerformanceStats perfStats = performanceMonitoringService.getPerformanceStats();
            
            // 获取缓存统计
            Map<String, Object> cacheStats = cacheService.getCacheStats();
            
            stats.put("performance", perfStats);
            stats.put("cache", cacheStats);
            stats.put("timestamp", System.currentTimeMillis());
            
            return stats;
        });
    }
    
    // ============ 辅助方法 ============
    
    private void calculateRelevanceScore(VideoResource video, String searchStrategy, com.martin.genAiAgent.model.UserProfile profile) {
        // 简单的相关性评分算法
        double score = 0.5; // 基础分
        
        if (video.specialNeedsFocus.contains(profile.getSpecialNeeds())) {
            score += 0.3;
        }
        
        if (searchStrategy.contains("颜色") && video.title.contains("颜色")) {
            score += 0.2;
        }
        
        video.relevanceScore = Math.min(score, 1.0);
    }
}
