package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 协同过滤服务
 */
@Service
public class CollaborativeFilteringService {
    
    private final Map<String, Map<String, Double>> userItemMatrix = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> itemUserMatrix = new ConcurrentHashMap<>();
    private final Map<String, Double> itemAverages = new ConcurrentHashMap<>();
    
    /**
     * 协同过滤推荐
     */
    public Flux<VideoResource> collaborativeFiltering(String userId, String specialNeeds, String ageRange, int maxResults) {
        return Mono.fromCallable(() -> {
            // 1. 找到相似用户
            Set<String> similarUsers = findSimilarUsers(userId, 50);
            
            // 2. 基于相似用户的偏好计算推荐分数
            Map<String, Double> recommendations = new HashMap<>();
            
            for (String similarUser : similarUsers) {
                Map<String, Double> userRatings = userItemMatrix.get(similarUser);
                if (userRatings != null) {
                    userRatings.forEach((itemId, rating) -> {
                        if (!userHasRated(userId, itemId)) {
                            double similarity = calculateUserSimilarity(userId, similarUser);
                            recommendations.merge(itemId, rating * similarity, Double::sum);
                        }
                    });
                }
            }
            
            // 3. 排序并返回推荐
            return recommendations.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(maxResults)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    ));
        })
        .flatMapMany(scoreMap -> {
            // 转换为VideoResource（这里需要从数据库获取实际视频）
            return Flux.fromIterable(scoreMap.entrySet())
                    .map(entry -> createMockVideoResource(entry.getKey(), entry.getValue()));
        });
    }
    
    /**
     * 更新协同过滤模型
     */
    public Mono<Void> updateModel(String userId, String videoId, double rating) {
        return Mono.fromRunnable(() -> {
            // 更新用户-物品矩阵
            userItemMatrix.computeIfAbsent(userId, k -> new HashMap<>()).put(videoId, rating);
            
            // 更新物品-用户矩阵
            itemUserMatrix.computeIfAbsent(videoId, k -> new HashMap<>()).put(userId, rating);
            
            // 更新物品平均分
            updateItemAverage(videoId);
        });
    }
    
    /**
     * 获取推荐解释
     */
    public Mono<String> getExplanation(String userId, String videoId) {
        return Mono.fromCallable(() -> {
            Set<String> similarUsers = findSimilarUsers(userId, 10);
            
            List<String> contributingUsers = similarUsers.stream()
                    .filter(user -> userHasRated(user, videoId))
                    .limit(3)
                    .collect(Collectors.toList());
            
            if (contributingUsers.isEmpty()) {
                return "基于协同过滤的相似用户推荐";
            }
            
            return String.format("与您相似的用户（%s）也喜欢这个内容", 
                String.join("、", contributingUsers));
        });
    }
    
    /**
     * 找到相似用户
     */
    private Set<String> findSimilarUsers(String userId, int limit) {
        Map<String, Double> similarities = new HashMap<>();
        
        for (String otherUser : userItemMatrix.keySet()) {
            if (!otherUser.equals(userId)) {
                double similarity = calculateUserSimilarity(userId, otherUser);
                if (similarity > 0.1) { // 相似度阈值
                    similarities.put(otherUser, similarity);
                }
            }
        }
        
        return similarities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
    
    /**
     * 计算用户相似度（余弦相似度）
     */
    private double calculateUserSimilarity(String user1, String user2) {
        Map<String, Double> ratings1 = userItemMatrix.get(user1);
        Map<String, Double> ratings2 = userItemMatrix.get(user2);
        
        if (ratings1 == null || ratings2 == null) {
            return 0.0;
        }
        
        Set<String> commonItems = new HashSet<>(ratings1.keySet());
        commonItems.retainAll(ratings2.keySet());
        
        if (commonItems.isEmpty()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String item : commonItems) {
            double rating1 = ratings1.get(item);
            double rating2 = ratings2.get(item);
            dotProduct += rating1 * rating2;
            norm1 += rating1 * rating1;
            norm2 += rating2 * rating2;
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 检查用户是否已评分
     */
    private boolean userHasRated(String userId, String videoId) {
        Map<String, Double> userRatings = userItemMatrix.get(userId);
        return userRatings != null && userRatings.containsKey(videoId);
    }
    
    /**
     * 更新物品平均分
     */
    private void updateItemAverage(String videoId) {
        Map<String, Double> ratings = itemUserMatrix.get(videoId);
        if (ratings != null && !ratings.isEmpty()) {
            double average = ratings.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            itemAverages.put(videoId, average);
        }
    }
    
    /**
     * 创建模拟视频资源
     */
    private VideoResource createMockVideoResource(String videoId, double score) {
        VideoResource video = new VideoResource();
        video.setId(videoId);
        video.setTitle("协同过滤推荐视频 " + videoId);
        video.setDescription("基于相似用户偏好推荐");
        video.setDuration("10分钟");
        video.setAgeRange("5-8岁");
        video.setTags(Arrays.asList("协同过滤", "个性化推荐"));
        video.setSource("Collaborative Filtering");
        video.setRelevanceScore(score);
        return video;
    }
}
