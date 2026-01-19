package com.martin.genAiAgent.service;

import org.springframework.stereotype.Service;
import com.martin.genAiAgent.model.VideoResource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐算法服务 - 实现多种推荐策略
 */
@Service
public class RecommendationAlgorithmService {
    
    /**
     * 推荐策略枚举
     */
    public enum RecommendationStrategy {
        CONTENT_BASED,    // 基于内容
        COLLABORATIVE,    // 协同过滤
        HYBRID,          // 混合推荐
        ADAPTIVE          // 自适应推荐
    }
    
    /**
     * 计算视频推荐分数
     */
    public double calculateRecommendationScore(VideoResource video, String specialNeeds, 
                                        String ageRange, List<String> userPreferences,
                                        Map<String, Integer> userFeedback) {
        double score = 0.0;
        
        // 1. 内容匹配分数 (40%)
        score += calculateContentMatchScore(video, specialNeeds, ageRange) * 0.4;
        
        // 2. 用户偏好分数 (30%)
        score += calculatePreferenceScore(video, userPreferences) * 0.3;
        
        // 3. 历史反馈分数 (20%)
        score += calculateFeedbackScore(video, userFeedback) * 0.2;
        
        // 4. 多样性分数 (10%)
        score += calculateDiversityScore(video, userPreferences) * 0.1;
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 内容匹配分数
     */
    private double calculateContentMatchScore(VideoResource video, String specialNeeds, String ageRange) {
        double score = 0.0;
        
        // 特殊需求匹配
        if (video.getSpecialNeedsFocus().contains(specialNeeds)) {
            score += 0.5;
        }
        
        // 年龄匹配
        if (isAgeMatch(video.getAgeRange(), ageRange)) {
            score += 0.3;
        }
        
        // 内容质量评估
        score += calculateContentQualityScore(video);
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 用户偏好分数
     */
    private double calculatePreferenceScore(VideoResource video, List<String> userPreferences) {
        if (userPreferences == null || userPreferences.isEmpty()) {
            return 0.5; // 默认分数
        }
        
        double score = 0.0;
        int matchCount = 0;
        
        for (String preference : userPreferences) {
            for (String tag : video.getTags()) {
                if (tag.toLowerCase().contains(preference.toLowerCase()) || 
                    preference.toLowerCase().contains(tag.toLowerCase())) {
                    matchCount++;
                    break;
                }
            }
        }
        
        score = (double) matchCount / Math.max(userPreferences.size(), 1);
        return Math.min(score, 1.0);
    }
    
    /**
     * 历史反馈分数
     */
    private double calculateFeedbackScore(VideoResource video, Map<String, Integer> userFeedback) {
        if (userFeedback == null || userFeedback.isEmpty()) {
            return 0.5; // 默认分数
        }
        
        // 查找相似视频的反馈
        double totalScore = 0.0;
        int count = 0;
        
        for (Map.Entry<String, Integer> feedback : userFeedback.entrySet()) {
            String videoId = feedback.getKey();
            int rating = feedback.getValue();
            
            // 如果是同一个视频或相似视频
            if (videoId.equals(video.getId()) || areVideosSimilar(videoId, video.getId())) {
                totalScore += rating / 5.0; // 归一化到0-1
                count++;
            }
        }
        
        return count > 0 ? totalScore / count : 0.5;
    }
    
    /**
     * 多样性分数
     */
    private double calculateDiversityScore(VideoResource video, List<String> userPreferences) {
        // 鼓励推荐不同类型的内容
        if (userPreferences == null || userPreferences.isEmpty()) {
            return 0.8; // 新用户给予较高多样性分数
        }
        
        // 检查是否推荐过相似内容
        boolean hasSimilarContent = userPreferences.stream()
                .anyMatch(pref -> video.getTags().stream()
                        .anyMatch(tag -> tag.toLowerCase().contains(pref.toLowerCase())));
        
        return hasSimilarContent ? 0.3 : 0.8;
    }
    
    /**
     * 内容质量评估
     */
    private double calculateContentQualityScore(VideoResource video) {
        double score = 0.0;
        
        // 时长适中 (5-15分钟为佳)
        try {
            int duration = extractDurationMinutes(video.getDuration());
            if (duration >= 5 && duration <= 15) {
                score += 0.3;
            } else if (duration >= 3 && duration <= 20) {
                score += 0.2;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        // 标签丰富度
        int tagCount = video.getTags().size();
        if (tagCount >= 3) {
            score += 0.2;
        } else if (tagCount >= 2) {
            score += 0.1;
        }
        
        return Math.min(score, 0.2);
    }
    
    /**
     * 年龄匹配检查
     */
    private boolean isAgeMatch(String videoAgeRange, String userAge) {
        // 简化的年龄匹配逻辑
        if (videoAgeRange.contains(userAge)) {
            return true;
        }
        
        // 检查年龄范围重叠
        try {
            int userAgeNum = Integer.parseInt(userAge.replaceAll("[^0-9]", ""));
            if (videoAgeRange.contains("3-6") && userAgeNum >= 3 && userAgeNum <= 6) return true;
            if (videoAgeRange.contains("4-7") && userAgeNum >= 4 && userAgeNum <= 7) return true;
            if (videoAgeRange.contains("5-8") && userAgeNum >= 5 && userAgeNum <= 8) return true;
            if (videoAgeRange.contains("6-9") && userAgeNum >= 6 && userAgeNum <= 9) return true;
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        return false;
    }
    
    /**
     * 提取时长分钟数
     */
    private int extractDurationMinutes(String duration) {
        if (duration.contains("分钟")) {
            return Integer.parseInt(duration.replaceAll("[^0-9]", ""));
        }
        return 10; // 默认10分钟
    }
    
    /**
     * 判断视频是否相似
     */
    private boolean areVideosSimilar(String videoId1, String videoId2) {
        // 简化的相似性判断
        return videoId1.split("_")[0].equals(videoId2.split("_")[0]);
    }
    
    /**
     * 应用推荐策略
     */
    public List<VideoResource> applyRecommendationStrategy(List<VideoResource> candidates, 
                                                      RecommendationStrategy strategy,
                                                      String specialNeeds,
                                                      String ageRange,
                                                      List<String> userPreferences,
                                                      Map<String, Integer> userFeedback) {
        
        switch (strategy) {
            case CONTENT_BASED:
                return applyContentBasedFiltering(candidates, specialNeeds, ageRange);
                
            case COLLABORATIVE:
                return applyCollaborativeFiltering(candidates, userFeedback);
                
            case HYBRID:
                return applyHybridRecommendation(candidates, specialNeeds, ageRange, 
                                            userPreferences, userFeedback);
                
            case ADAPTIVE:
                return applyAdaptiveRecommendation(candidates, specialNeeds, ageRange, 
                                             userPreferences, userFeedback);
                
            default:
                return candidates;
        }
    }
    
    /**
     * 基于内容的过滤
     */
    private List<VideoResource> applyContentBasedFiltering(List<VideoResource> candidates, 
                                                     String specialNeeds, String ageRange) {
        return candidates.stream()
                .filter(video -> video.getSpecialNeedsFocus().contains(specialNeeds))
                .filter(video -> isAgeMatch(video.getAgeRange(), ageRange))
                .collect(Collectors.toList());
    }
    
    /**
     * 协同过滤
     */
    private List<VideoResource> applyCollaborativeFiltering(List<VideoResource> candidates, 
                                                      Map<String, Integer> userFeedback) {
        if (userFeedback == null || userFeedback.isEmpty()) {
            return candidates;
        }
        
        // 基于用户历史偏好推荐相似内容
        return candidates.stream()
                .sorted((a, b) -> Double.compare(
                    calculateFeedbackScore(b, userFeedback),
                    calculateFeedbackScore(a, userFeedback)))
                .collect(Collectors.toList());
    }
    
    /**
     * 混合推荐
     */
    private List<VideoResource> applyHybridRecommendation(List<VideoResource> candidates, 
                                                     String specialNeeds, String ageRange,
                                                     List<String> userPreferences,
                                                     Map<String, Integer> userFeedback) {
        return candidates.stream()
                .map(video -> {
                    double score = calculateRecommendationScore(video, specialNeeds, ageRange, 
                                                        userPreferences, userFeedback);
                    video.setRelevanceScore(score);
                    return video;
                })
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * 自适应推荐
     */
    private List<VideoResource> applyAdaptiveRecommendation(List<VideoResource> candidates, 
                                                      String specialNeeds, String ageRange,
                                                      List<String> userPreferences,
                                                      Map<String, Integer> userFeedback) {
        // 根据用户历史数据动态调整策略权重
        final double contentWeight;
        final double preferenceWeight;
        final double feedbackWeight;
        final double diversityWeight;
        
        // 如果用户有丰富的反馈历史，增加反馈权重
        if (userFeedback != null && userFeedback.size() > 5) {
            feedbackWeight = 0.4;
            contentWeight = 0.3;
            preferenceWeight = 0.2;
            diversityWeight = 0.1;
        }
        // 如果是新用户，增加内容匹配权重
        else if (userFeedback == null || userFeedback.isEmpty()) {
            contentWeight = 0.6;
            preferenceWeight = 0.2;
            feedbackWeight = 0.1;
            diversityWeight = 0.1;
        }
        else {
            contentWeight = 0.4;
            preferenceWeight = 0.3;
            feedbackWeight = 0.2;
            diversityWeight = 0.1;
        }
        
        return candidates.stream()
                .map(video -> {
                    double score = 0.0;
                    score += calculateContentMatchScore(video, specialNeeds, ageRange) * contentWeight;
                    score += calculatePreferenceScore(video, userPreferences) * preferenceWeight;
                    score += calculateFeedbackScore(video, userFeedback) * feedbackWeight;
                    score += calculateDiversityScore(video, userPreferences) * diversityWeight;
                    
                    video.setRelevanceScore(Math.min(score, 1.0));
                    return video;
                })
                .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取推荐解释
     */
    public String getRecommendationExplanation(VideoResource video, double score, 
                                         RecommendationStrategy strategy) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append(String.format("推荐分数: %.2f\n", score));
        explanation.append(String.format("推荐策略: %s\n", strategy));
        
        if (video.getSpecialNeedsFocus().contains("自闭症")) {
            explanation.append("• 适合自闭症儿童的视觉学习方式\n");
        }
        if (video.getTags().contains("互动")) {
            explanation.append("• 互动性强，有助于保持注意力\n");
        }
        if (video.getTags().contains("步骤分解")) {
            explanation.append("• 步骤清晰，便于理解\n");
        }
        
        return explanation.toString();
    }
}
