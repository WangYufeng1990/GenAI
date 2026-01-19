package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 机器学习推荐服务 - 实现高级推荐算法
 */
@Service
public class MachineLearningRecommendationService {
    
    private final UserBehaviorAnalysisService behaviorAnalysisService;
    private final CollaborativeFilteringService collaborativeService;
    private final ContentBasedFilteringService contentBasedService;
    private final DeepLearningService deepLearningService;
    
    public MachineLearningRecommendationService(UserBehaviorAnalysisService behaviorAnalysisService,
                                          CollaborativeFilteringService collaborativeService,
                                          ContentBasedFilteringService contentBasedService,
                                          DeepLearningService deepLearningService) {
        this.behaviorAnalysisService = behaviorAnalysisService;
        this.collaborativeService = collaborativeService;
        this.contentBasedService = contentBasedService;
        this.deepLearningService = deepLearningService;
    }
    
    /**
     * 混合推荐算法
     */
    public Flux<VideoResource> hybridRecommendation(String userId, String specialNeeds, String ageRange, 
                                                 List<String> learningGoals, int maxResults) {
        
        return Mono.zip(
            // 1. 协同过滤推荐
            collaborativeService.collaborativeFiltering(userId, specialNeeds, ageRange, maxResults / 2).collectList(),
            
            // 2. 基于内容的推荐
            contentBasedService.contentBasedFiltering(specialNeeds, ageRange, learningGoals, maxResults / 2).collectList(),
            
            // 3. 深度学习推荐
            deepLearningService.deepLearningRecommendation(userId, specialNeeds, ageRange, learningGoals, maxResults / 2).collectList(),
            
            // 4. 用户行为分析
            behaviorAnalysisService.analyzeUserBehavior(userId, specialNeeds, ageRange)
        )
        .flatMapMany(tuple -> {
            List<VideoResource> collaborativeResults = tuple.getT1();
            List<VideoResource> contentBasedResults = tuple.getT2();
            List<VideoResource> deepLearningResults = tuple.getT3();
            Map<String, Double> behaviorScores = tuple.getT4();
            
            // 合并和加权
            return mergeAndWeightRecommendations(
                collaborativeResults, 
                contentBasedResults, 
                deepLearningResults, 
                behaviorScores,
                userId
            );
        })
        .take(maxResults);
    }
    
    /**
     * 合并和加权推荐结果
     */
    private Flux<VideoResource> mergeAndWeightRecommendations(
            List<VideoResource> collaborativeResults,
            List<VideoResource> contentBasedResults,
            List<VideoResource> deepLearningResults,
            Map<String, Double> behaviorScores,
            String userId) {
        
        Map<String, VideoResource> allVideos = new HashMap<>();
        Map<String, Double> videoScores = new HashMap<>();
        
        // 协同过滤权重：30%
        addWeightedScores(collaborativeResults, videoScores, 0.3, "collaborative");
        
        // 基于内容权重：25%
        addWeightedScores(contentBasedResults, videoScores, 0.25, "content");
        
        // 深度学习权重：35%
        addWeightedScores(deepLearningResults, videoScores, 0.35, "deep_learning");
        
        // 用户行为权重：10%
        behaviorScores.forEach((videoId, score) -> {
            videoScores.merge(videoId, score * 0.1, Double::sum);
        });
        
        // 收集所有视频
        collaborativeResults.forEach(video -> allVideos.put(video.getId(), video));
        contentBasedResults.forEach(video -> allVideos.put(video.getId(), video));
        deepLearningResults.forEach(video -> allVideos.put(video.getId(), video));
        
        // 按分数排序
        return Mono.fromCallable(() -> {
            return videoScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(20) // 限制结果数量
                    .map(entry -> {
                        VideoResource video = allVideos.get(entry.getKey());
                        if (video != null) {
                            video.setRelevanceScore(entry.getValue());
                            return video;
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }).flatMapMany(Flux::fromIterable);
    }
    
    /**
     * 添加加权分数
     */
    private void addWeightedScores(List<VideoResource> videos, Map<String, Double> videoScores, 
                                double weight, String source) {
        for (int i = 0; i < videos.size(); i++) {
            VideoResource video = videos.get(i);
            String videoId = video.getId();
            
            // 基于排名的分数衰减
            double rankScore = 1.0 / (i + 1);
            double weightedScore = video.getRelevanceScore() * rankScore * weight;
            
            videoScores.merge(videoId, weightedScore, Double::sum);
        }
    }
    
    /**
     * 实时学习更新
     */
    public Mono<Void> updateModel(String userId, String videoId, double rating, 
                              long watchTime, boolean completed) {
        return Mono.zip(
            // 更新协同过滤模型
            collaborativeService.updateModel(userId, videoId, rating),
            
            // 更新内容过滤模型
            contentBasedService.updateModel(userId, videoId, rating),
            
            // 更新深度学习模型
            deepLearningService.updateModel(userId, videoId, rating, watchTime, completed),
            
            // 更新用户行为分析
            behaviorAnalysisService.recordInteraction(userId, videoId, rating, watchTime, completed)
        ).then();
    }
    
    /**
     * 冷启动处理
     */
    public Flux<VideoResource> coldStartRecommendation(String specialNeeds, String ageRange, 
                                                   List<String> learningGoals, int maxResults) {
        // 新用户使用基于内容的推荐
        return contentBasedService.contentBasedFiltering(specialNeeds, ageRange, learningGoals, maxResults)
                .map(video -> {
                    // 冷启动时给予较高的探索性分数
                    video.setRelevanceScore(video.getRelevanceScore() * 0.8);
                    return video;
                });
    }
    
    /**
     * 推荐解释生成
     */
    public Mono<String> generateRecommendationExplanation(String userId, VideoResource video) {
        return Mono.zip(
            collaborativeService.getExplanation(userId, video.getId()),
            contentBasedService.getExplanation(video.getId()),
            deepLearningService.getExplanation(userId, video.getId()),
            behaviorAnalysisService.getBehaviorExplanation(userId, video.getId())
        )
        .map(tuple -> {
            String collaborativeExplanation = tuple.getT1();
            String contentExplanation = tuple.getT2();
            String deepLearningExplanation = tuple.getT3();
            String behaviorExplanation = tuple.getT4();
            
            StringBuilder explanation = new StringBuilder();
            explanation.append("推荐理由：\n");
            
            if (!collaborativeExplanation.isEmpty()) {
                explanation.append("• ").append(collaborativeExplanation).append("\n");
            }
            if (!contentExplanation.isEmpty()) {
                explanation.append("• ").append(contentExplanation).append("\n");
            }
            if (!deepLearningExplanation.isEmpty()) {
                explanation.append("• ").append(deepLearningExplanation).append("\n");
            }
            if (!behaviorExplanation.isEmpty()) {
                explanation.append("• ").append(behaviorExplanation).append("\n");
            }
            
            return explanation.toString();
        });
    }
    
    /**
     * A/B 测试框架
     */
    public Flux<VideoResource> abTestRecommendation(String userId, String specialNeeds, String ageRange,
                                                 List<String> learningGoals, int maxResults) {
        // 根据用户ID分配到不同的测试组
        int userHash = Math.abs(userId.hashCode());
        String testGroup = userHash % 3 == 0 ? "A" : (userHash % 3 == 1 ? "B" : "C");
        
        switch (testGroup) {
            case "A":
                // A组：传统协同过滤
                return collaborativeService.collaborativeFiltering(userId, specialNeeds, ageRange, maxResults)
                        .doOnNext(video -> video.setRelevanceScore(video.getRelevanceScore() * 0.9));
                        
            case "B":
                // B组：深度学习模型
                return deepLearningService.deepLearningRecommendation(userId, specialNeeds, ageRange, learningGoals, maxResults)
                        .doOnNext(video -> video.setRelevanceScore(video.getRelevanceScore() * 1.1));
                        
            default:
                // C组：混合模型（对照组）
                return hybridRecommendation(userId, specialNeeds, ageRange, learningGoals, maxResults);
        }
    }
    
    /**
     * 推荐多样性优化
     */
    public Flux<VideoResource> diversifyRecommendations(List<VideoResource> recommendations, 
                                                   double diversityThreshold) {
        if (recommendations.size() <= 5) {
            return Flux.fromIterable(recommendations);
        }
        
        List<VideoResource> diversified = new ArrayList<>();
        Set<String> usedTags = new HashSet<>();
        Set<String> usedSources = new HashSet<>();
        
        // 首先选择高分且多样化的内容
        for (VideoResource video : recommendations) {
            boolean isDiverse = true;
            
            // 检查标签多样性
            for (String tag : video.getTags()) {
                if (usedTags.contains(tag) && usedTags.size() > 3) {
                    isDiverse = false;
                    break;
                }
            }
            
            // 检查来源多样性
            if (usedSources.contains(video.getSource()) && usedSources.size() > 2) {
                isDiverse = false;
            }
            
            if (isDiverse || diversified.size() < 3) {
                diversified.add(video);
                usedTags.addAll(video.getTags());
                usedSources.add(video.getSource());
            }
            
            if (diversified.size() >= 10) {
                break;
            }
        }
        
        return Flux.fromIterable(diversified);
    }
    
    /**
     * 推荐质量评估
     */
    public Mono<Map<String, Double>> evaluateRecommendationQuality(String userId, 
                                                           List<VideoResource> recommendations) {
        return Mono.fromCallable(() -> {
            Map<String, Double> metrics = new HashMap<>();
            
            // 1. 覆盖度指标
            Set<String> allTags = recommendations.stream()
                    .flatMap(video -> video.getTags().stream())
                    .collect(Collectors.toSet());
            metrics.put("coverage", (double) allTags.size() / 20.0); // 假设有20个可能的标签
            
            // 2. 多样性指标
            Set<String> sources = recommendations.stream()
                    .map(VideoResource::getSource)
                    .collect(Collectors.toSet());
            metrics.put("diversity", (double) sources.size() / 5.0); // 假设有5个可能的来源
            
            // 3. 新颖性指标
            double noveltyScore = recommendations.stream()
                    .mapToDouble(video -> video.getRelevanceScore() < 0.5 ? 1.0 : 0.0)
                    .average()
                    .orElse(0.0);
            metrics.put("novelty", noveltyScore);
            
            // 4. 准确性指标（基于历史数据）
            double accuracyScore = recommendations.stream()
                    .mapToDouble(VideoResource::getRelevanceScore)
                    .average()
                    .orElse(0.0);
            metrics.put("accuracy", accuracyScore);
            
            return metrics;
        });
    }
}
