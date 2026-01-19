package com.martin.genAiAgent.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户行为分析服务
 */
@Service
public class UserBehaviorAnalysisService {
    
    private final Map<String, List<UserInteraction>> userInteractions = new ConcurrentHashMap<>();
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    
    /**
     * 用户交互记录
     */
    public static class UserInteraction {
        String userId;
        String videoId;
        double rating;
        long watchTime;
        boolean completed;
        LocalDateTime timestamp;
        String interactionType; // click, watch, rate, share
        
        public UserInteraction(String userId, String videoId, double rating, long watchTime, boolean completed, String interactionType) {
            this.userId = userId;
            this.videoId = videoId;
            this.rating = rating;
            this.watchTime = watchTime;
            this.completed = completed;
            this.timestamp = LocalDateTime.now();
            this.interactionType = interactionType;
        }
    }
    
    /**
     * 用户画像
     */
    public static class UserProfile {
        String userId;
        Map<String, Integer> tagPreferences = new HashMap<>();
        Map<String, Integer> sourcePreferences = new HashMap<>();
        Map<String, Double> specialNeedsScores = new HashMap<>();
        double averageWatchTime;
        double completionRate;
        List<String> favoriteTags = new ArrayList<>();
        String learningStyle; // visual, auditory, kinesthetic
        
        public UserProfile(String userId) {
            this.userId = userId;
        }
    }
    
    /**
     * 记录用户交互
     */
    public Mono<Void> recordInteraction(String userId, String videoId, double rating, 
                                   long watchTime, boolean completed) {
        return Mono.fromRunnable(() -> {
            UserInteraction interaction = new UserInteraction(userId, videoId, rating, watchTime, completed, "watch");
            
            userInteractions.computeIfAbsent(userId, k -> new ArrayList<>()).add(interaction);
            
            // 更新用户画像
            updateUserProfile(userId, interaction);
        });
    }
    
    /**
     * 分析用户行为
     */
    public Mono<Map<String, Double>> analyzeUserBehavior(String userId, String specialNeeds, String ageRange) {
        return Mono.fromCallable(() -> {
            UserProfile profile = userProfiles.computeIfAbsent(userId, UserProfile::new);
            
            Map<String, Double> behaviorScores = new HashMap<>();
            
            // 1. 基于历史偏好的分数
            profile.tagPreferences.forEach((tag, count) -> {
                double score = Math.log(count + 1) / Math.log(10); // 归一化
                behaviorScores.put("tag_" + tag, score);
            });
            
            // 2. 基于完成率的分数
            behaviorScores.put("completion_rate", profile.completionRate);
            
            // 3. 基于观看时长的分数
            behaviorScores.put("watch_time_preference", profile.averageWatchTime / 600.0); // 标准化到分钟
            
            // 4. 基于特殊需求适配的分数
            behaviorScores.put("special_needs_fit", profile.specialNeedsScores.getOrDefault(specialNeeds, 0.5));
            
            // 5. 基于学习风格的分数
            behaviorScores.put("learning_style_match", getLearningStyleScore(profile.learningStyle));
            
            return behaviorScores;
        });
    }
    
    /**
     * 更新用户画像
     */
    private void updateUserProfile(String userId, UserInteraction interaction) {
        UserProfile profile = userProfiles.computeIfAbsent(userId, UserProfile::new);
        
        // 更新标签偏好
        // 这里需要从视频ID获取标签信息，简化实现
        String[] mockTags = {"数学", "科学", "阅读", "音乐", "美术"};
        for (String tag : mockTags) {
            profile.tagPreferences.merge(tag, 1, Integer::sum);
        }
        
        // 更新平均观看时长
        List<UserInteraction> interactions = userInteractions.get(userId);
        profile.averageWatchTime = interactions.stream()
                .mapToLong(i -> i.watchTime)
                .average()
                .orElse(0.0);
        
        // 更新完成率
        long completedCount = interactions.stream()
                .filter(i -> i.completed)
                .count();
        profile.completionRate = (double) completedCount / interactions.size();
        
        // 更新最喜欢的标签
        profile.favoriteTags = profile.tagPreferences.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // 推断学习风格
        profile.learningStyle = inferLearningStyle(interactions);
    }
    
    /**
     * 推断学习风格
     */
    private String inferLearningStyle(List<UserInteraction> interactions) {
        // 简化的学习风格推断
        long visualCount = interactions.stream()
                .filter(i -> i.videoId.contains("visual") || i.videoId.contains("animation"))
                .count();
        
        long auditoryCount = interactions.stream()
                .filter(i -> i.videoId.contains("audio") || i.videoId.contains("music"))
                .count();
        
        long kinestheticCount = interactions.stream()
                .filter(i -> i.videoId.contains("interactive") || i.videoId.contains("game"))
                .count();
        
        if (visualCount > auditoryCount && visualCount > kinestheticCount) {
            return "visual";
        } else if (auditoryCount > visualCount && auditoryCount > kinestheticCount) {
            return "auditory";
        } else if (kinestheticCount > visualCount && kinestheticCount > auditoryCount) {
            return "kinesthetic";
        }
        
        return "mixed";
    }
    
    /**
     * 获取学习风格分数
     */
    private double getLearningStyleScore(String learningStyle) {
        switch (learningStyle) {
            case "visual": return 0.9;
            case "auditory": return 0.8;
            case "kinesthetic": return 0.85;
            case "mixed": return 0.7;
            default: return 0.5;
        }
    }
    
    /**
     * 获取行为解释
     */
    public Mono<String> getBehaviorExplanation(String userId, String videoId) {
        return Mono.fromCallable(() -> {
            UserProfile profile = userProfiles.get(userId);
            if (profile == null) {
                return "新用户，基于通用推荐";
            }
            
            StringBuilder explanation = new StringBuilder();
            
            // 基于最喜欢的标签
            if (!profile.favoriteTags.isEmpty()) {
                explanation.append("符合您喜欢的").append(profile.favoriteTags.get(0)).append("内容类型；");
            }
            
            // 基于学习风格
            if (profile.learningStyle != null) {
                explanation.append("适合您的").append(getLearningStyleDescription(profile.learningStyle)).append("学习风格；");
            }
            
            // 基于完成率
            if (profile.completionRate > 0.8) {
                explanation.append("与您高完成率的内容相似；");
            }
            
            return explanation.toString();
        });
    }
    
    /**
     * 获取学习风格描述
     */
    private String getLearningStyleDescription(String learningStyle) {
        switch (learningStyle) {
            case "visual": return "视觉型";
            case "auditory": return "听觉型";
            case "kinesthetic": return "动手型";
            case "mixed": return "混合型";
            default: return "未知";
        }
    }
    
    /**
     * 获取用户统计信息
     */
    public Mono<Map<String, Object>> getUserStats(String userId) {
        return Mono.fromCallable(() -> {
            List<UserInteraction> interactions = userInteractions.get(userId);
            UserProfile profile = userProfiles.get(userId);
            
            Map<String, Object> stats = new HashMap<>();
            
            if (interactions != null) {
                stats.put("total_interactions", interactions.size());
                stats.put("average_rating", interactions.stream()
                        .mapToDouble(i -> i.rating)
                        .average()
                        .orElse(0.0));
                stats.put("completion_rate", profile != null ? profile.completionRate : 0.0);
            }
            
            if (profile != null) {
                stats.put("favorite_tags", profile.favoriteTags);
                stats.put("learning_style", profile.learningStyle);
                stats.put("average_watch_time", profile.averageWatchTime);
            }
            
            return stats;
        });
    }
    
    /**
     * 清理过期数据
     */
    public Mono<Void> cleanupOldData() {
        return Mono.fromRunnable(() -> {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90); // 保留90天数据
            
            userInteractions.entrySet().removeIf(entry -> {
                List<UserInteraction> interactions = entry.getValue();
                interactions.removeIf(interaction -> interaction.timestamp.isBefore(cutoff));
                return interactions.isEmpty();
            });
        });
    }
}
