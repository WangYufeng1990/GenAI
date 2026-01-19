package com.martin.genAiAgent.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.martin.genAiAgent.model.VideoResource;
import com.martin.genAiAgent.service.VideoSearchService;
import com.martin.genAiAgent.service.VectorSearchService;
import com.martin.genAiAgent.service.RecommendationAlgorithmService;
import com.martin.genAiAgent.service.MachineLearningRecommendationService;
import com.martin.genAiAgent.service.ABTestingService;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Educational Resource AI Agent
 * 为特殊儿童提供个性化教育资源推荐的智能代理
 */
@RestController
@RequestMapping("/agent")
public class EducationalResourceAgent {
    
    private final ChatClient chatClient;
    private final VideoSearchService videoSearchService;
    private final VectorSearchService vectorSearchService;
    private final RecommendationAlgorithmService recommendationAlgorithmService;
    private final MachineLearningRecommendationService mlRecommendationService;
    private final ABTestingService abTestingService;
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    
    // 用户画像存储
    private static class UserProfile {
        String childAge;
        String specialNeeds; // 自闭症、多动症、学习障碍等
        String learningGoals;
        List<String> preferences;
        List<String> previouslyRecommended; // 避免重复推荐
        Map<String, Integer> feedbackScores; // 用户反馈评分
        
        UserProfile(String childAge, String specialNeeds, String learningGoals) {
            this.childAge = childAge;
            this.specialNeeds = specialNeeds;
            this.learningGoals = learningGoals;
            this.preferences = new ArrayList<>();
            this.previouslyRecommended = new ArrayList<>();
            this.feedbackScores = new HashMap<>();
        }
    }
    
    public EducationalResourceAgent(ChatClient chatClient,
                              VideoSearchService videoSearchService,
                              VectorSearchService vectorSearchService,
                              RecommendationAlgorithmService recommendationAlgorithmService,
                              MachineLearningRecommendationService mlRecommendationService,
                              ABTestingService abTestingService) {
        this.chatClient = chatClient;
        this.videoSearchService = videoSearchService;
        this.vectorSearchService = vectorSearchService;
        this.recommendationAlgorithmService = recommendationAlgorithmService;
        this.mlRecommendationService = mlRecommendationService;
        this.abTestingService = abTestingService;
    }
    
    /**
     * Agent 主入口 - 智能教育资源推荐
     */
    @RequestMapping(value = "/educational-resources", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/event-stream")
    public Flux<String> findEducationalResources(
            @RequestParam String userId,
            @RequestParam String childAge,
            @RequestParam String specialNeeds,
            @RequestParam String learningGoal,
            @RequestParam(defaultValue = "5") int maxResults
    ) {
        
        // 1. 创建或更新用户画像
        UserProfile profile = userProfiles.computeIfAbsent(userId, k -> 
            new UserProfile(childAge, specialNeeds, learningGoal));
        
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
                                        profile.specialNeeds, 
                                        profile.childAge, 
                                        profile.preferences, 
                                        profile.feedbackScores
                                    );
                                    return Flux.fromIterable(rankedVideos);
                                });
                })
                .take(maxResults)
                .map(this::formatRecommendation);
    }
    
    /**
     * 步骤1: AI 分析用户需求
     */
    private Mono<String> analyzeUserNeeds(UserProfile profile, String learningGoal) {
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
            profile.childAge, 
            profile.specialNeeds, 
            learningGoal,
            profile.preferences);
        
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
    private Flux<VideoResource> searchEducationalVideos(String searchStrategy, UserProfile profile) {
        // 从AI搜索策略中提取关键词
        String query = extractQueryFromStrategy(searchStrategy);
        
        // 1. 传统关键词搜索
        Flux<VideoResource> keywordResults = videoSearchService.searchEducationalVideos(
            query, profile.specialNeeds, profile.childAge);
        
        // 2. 向量语义搜索
        List<VideoResource> vectorResults = vectorSearchService.semanticSearch(
            query, profile.specialNeeds, profile.childAge, 10);
        
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
    private Flux<VideoResource> rankAndRecommendVideos(List<VideoResource> videos, UserProfile profile) {
        // 使用推荐算法服务进行智能排序
        List<VideoResource> rankedVideos = recommendationAlgorithmService.applyRecommendationStrategy(
            videos, 
            RecommendationAlgorithmService.RecommendationStrategy.ADAPTIVE,
            profile.specialNeeds,
            profile.childAge,
            profile.preferences,
            profile.feedbackScores
        );
        
        return Flux.fromIterable(rankedVideos)
                .filter(video -> !profile.previouslyRecommended.contains(video.id));
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
    public Mono<String> recordFeedback(
            @RequestParam String userId,
            @RequestParam String videoId,
            @RequestParam int rating,
            @RequestParam(defaultValue = "0") long watchTimeParam,
            @RequestParam(defaultValue = "false") boolean completedParam) {
        
        // 更新用户反馈
        UserProfile profile = userProfiles.get(userId);
        if (profile != null) {
            profile.feedbackScores.put(videoId, rating);
        }
        
        // 更新机器学习模型
        return mlRecommendationService.updateModel(userId, videoId, rating, watchTimeParam, completedParam)
                .and(abTestingService.recordUserInteraction(userId, "ml_test", videoId, "rate", rating))
                .map(v -> "反馈已记录，评分：" + rating);
    }
    
    /**
     * 获取A/B测试统计
     */
    @GetMapping("/ab-test-stats/{testId}")
    public Mono<Map<String, Object>> getABTestStats(@PathVariable String testId) {
        return abTestingService.getTestStatistics(testId);
    }
    /**
     * AI 学习用户反馈
     */
    private Mono<String> learnFromFeedback(UserProfile profile, String videoId, int rating) {
        String prompt = String.format("""
            用户对视频ID %s 给出了 %d 星评分。
            请分析这个反馈并更新用户偏好模型：
            
            当前用户画像：
            - 年龄：%s
            - 特殊需求：%s
            - 学习目标：%s
            - 之前偏好：%s
            
            请分析：
            1. 这个评分说明了什么偏好
            2. 如何调整推荐策略
            3. 未来应该推荐什么类型的内容
            """, 
            videoId, rating, profile.childAge, profile.specialNeeds, 
            profile.learningGoals, profile.preferences);
        
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .collectList()
                .map(results -> {
                    String analysis = String.join("", results);
                    // 这里可以解析AI的分析结果，更新用户偏好
                    profile.preferences.add("AI分析结果：" + analysis.substring(0, 50));
                    return analysis;
                });
    }
    
    // ============ 辅助方法 ============
    
    private boolean isVideoSuitable(VideoResource video, UserProfile profile) {
        return video.ageRange.contains(profile.childAge) || 
               video.specialNeedsFocus.contains(profile.specialNeeds);
    }
    
    private void calculateRelevanceScore(VideoResource video, String searchStrategy, UserProfile profile) {
        // 简单的相关性评分算法
        double score = 0.5; // 基础分
        
        if (video.specialNeedsFocus.contains(profile.specialNeeds)) {
            score += 0.3;
        }
        
        // 根据搜索策略调整分数
        if (searchStrategy.toLowerCase().contains("视觉") && 
            video.tags.contains("视觉学习")) {
            score += 0.2;
        }
        
        video.relevanceScore = Math.min(score, 1.0);
    }
    
    // 模拟视频数据库
    private VideoResource[] getMockVideoResources() {
        return new VideoResource[] {
            new VideoResource("v1", "颜色学习 - 自闭症儿童友好版", 
                "通过缓慢的动画和重复学习颜色识别", "10分钟", "3-6岁",
                Arrays.asList("颜色识别", "视觉学习", "重复练习"), "自闭症"),
                
            new VideoResource("v2", "数字1-10 - 多动症儿童版", 
                "快节奏的数字学习，包含互动元素", "8分钟", "4-7岁",
                Arrays.asList("数字学习", "互动", "快节奏"), "多动症"),
                
            new VideoResource("v3", "社交技能训练 - 情绪识别", 
                "帮助特殊需求儿童理解面部表情和情绪", "12分钟", "5-8岁",
                Arrays.asList("社交技能", "情绪识别", "面部表情"), "社交障碍"),
                
            new VideoResource("v4", "字母学习 - 感觉统合版", 
                "结合触觉和视觉的字母学习体验", "15分钟", "3-6岁",
                Arrays.asList("字母学习", "感觉统合", "多感官"), "感觉统合障碍"),
                
            new VideoResource("v5", "简单数学 - 步骤分解版", 
                "将数学概念分解为小步骤，适合学习障碍儿童", "10分钟", "6-9岁",
                Arrays.asList("数学", "步骤分解", "重复练习"), "学习障碍")
        };
    }
}
