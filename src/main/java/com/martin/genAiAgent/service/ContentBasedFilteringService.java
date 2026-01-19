package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内容的过滤服务
 */
@Service
public class ContentBasedFilteringService {
    
    private final Map<String, VideoContent> videoContentMap = new ConcurrentHashMap<>();
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    
    /**
     * 视频内容特征
     */
    public static class VideoContent {
        String videoId;
        Set<String> tags;
        Set<String> keywords;
        String specialNeeds;
        String ageRange;
        String source;
        double duration;
        Map<String, Double> features;
        
        public VideoContent(String videoId) {
            this.videoId = videoId;
            this.tags = new HashSet<>();
            this.keywords = new HashSet<>();
            this.features = new HashMap<>();
        }
    }
    
    /**
     * 用户画像
     */
    public static class UserProfile {
        String userId;
        Map<String, Double> tagPreferences = new HashMap<>();
        Map<String, Double> keywordPreferences = new HashMap<>();
        String preferredSpecialNeeds;
        String preferredAgeRange;
        Map<String, Double> sourcePreferences = new HashMap<>();
        
        public UserProfile(String userId) {
            this.userId = userId;
        }
    }
    
    /**
     * 基于内容的过滤推荐
     */
    public Flux<VideoResource> contentBasedFiltering(String specialNeeds, String ageRange, 
                                                     List<String> learningGoals, int maxResults) {
        return Mono.fromCallable(() -> {
            // 1. 构建用户偏好向量
            Map<String, Double> userVector = buildUserPreferenceVector(specialNeeds, ageRange, learningGoals);
            
            // 2. 计算所有视频的内容相似度
            Map<String, Double> similarities = new HashMap<>();
            
            for (VideoContent content : videoContentMap.values()) {
                double similarity = calculateContentSimilarity(userVector, content);
                if (similarity > 0.1) {
                    similarities.put(content.videoId, similarity);
                }
            }
            
            // 3. 排序并返回推荐
            return similarities.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(maxResults)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    ));
        })
        .flatMapMany(scoreMap -> {
            return Flux.fromIterable(scoreMap.entrySet())
                    .map(entry -> createVideoResourceFromContent(entry.getKey(), entry.getValue()));
        });
    }
    
    /**
     * 更新内容过滤模型
     */
    public Mono<Void> updateModel(String userId, String videoId, double rating) {
        return Mono.fromRunnable(() -> {
            UserProfile profile = userProfiles.computeIfAbsent(userId, UserProfile::new);
            VideoContent content = videoContentMap.get(videoId);
            
            if (content != null) {
                // 更新标签偏好
                for (String tag : content.tags) {
                    profile.tagPreferences.merge(tag, rating, Double::sum);
                }
                
                // 更新关键词偏好
                for (String keyword : content.keywords) {
                    profile.keywordPreferences.merge(keyword, rating, Double::sum);
                }
                
                // 更新来源偏好
                profile.sourcePreferences.merge(content.source, rating, Double::sum);
            }
        });
    }
    
    /**
     * 获取推荐解释
     */
    public Mono<String> getExplanation(String videoId) {
        return Mono.fromCallable(() -> {
            VideoContent content = videoContentMap.get(videoId);
            if (content == null) {
                return "基于内容特征的推荐";
            }
            
            StringBuilder explanation = new StringBuilder();
            explanation.append("基于内容特征：");
            
            if (!content.tags.isEmpty()) {
                explanation.append("标签匹配（").append(String.join("、", content.tags)).append("）；");
            }
            
            if (!content.keywords.isEmpty()) {
                explanation.append("关键词匹配（").append(String.join("、", content.keywords)).append("）；");
            }
            
            if (content.specialNeeds != null) {
                explanation.append("特殊需求适配（").append(content.specialNeeds).append("）；");
            }
            
            return explanation.toString();
        });
    }
    
    /**
     * 构建用户偏好向量
     */
    private Map<String, Double> buildUserPreferenceVector(String specialNeeds, String ageRange, 
                                                         List<String> learningGoals) {
        Map<String, Double> vector = new HashMap<>();
        
        // 特殊需求权重
        if (specialNeeds != null) {
            vector.put("special_needs_" + specialNeeds, 1.0);
        }
        
        // 年龄范围权重
        if (ageRange != null) {
            vector.put("age_range_" + ageRange, 1.0);
        }
        
        // 学习目标权重
        if (learningGoals != null) {
            for (String goal : learningGoals) {
                vector.put("learning_goal_" + goal, 1.0);
            }
        }
        
        // 通用教育特征权重
        vector.put("educational", 0.8);
        vector.put("interactive", 0.7);
        vector.put("visual", 0.6);
        vector.put("engaging", 0.7);
        
        return vector;
    }
    
    /**
     * 计算内容相似度
     */
    private double calculateContentSimilarity(Map<String, Double> userVector, VideoContent content) {
        Map<String, Double> contentVector = new HashMap<>();
        
        // 转换内容特征为向量
        for (String tag : content.tags) {
            contentVector.put("tag_" + tag, 1.0);
        }
        
        for (String keyword : content.keywords) {
            contentVector.put("keyword_" + keyword, 1.0);
        }
        
        if (content.specialNeeds != null) {
            contentVector.put("special_needs_" + content.specialNeeds, 1.0);
        }
        
        if (content.ageRange != null) {
            contentVector.put("age_range_" + content.ageRange, 1.0);
        }
        
        // 计算余弦相似度
        return calculateCosineSimilarity(userVector, contentVector);
    }
    
    /**
     * 计算余弦相似度
     */
    private double calculateCosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        Set<String> commonKeys = new HashSet<>(vector1.keySet());
        commonKeys.retainAll(vector2.keySet());
        
        if (commonKeys.isEmpty()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (String key : commonKeys) {
            dotProduct += vector1.get(key) * vector2.get(key);
        }
        
        for (double value : vector1.values()) {
            norm1 += value * value;
        }
        
        for (double value : vector2.values()) {
            norm2 += value * value;
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 从内容创建视频资源
     */
    private VideoResource createVideoResourceFromContent(String videoId, double score) {
        VideoContent content = videoContentMap.get(videoId);
        if (content == null) {
            return createMockVideoResource(videoId, score);
        }
        
        VideoResource video = new VideoResource();
        video.setId(videoId);
        video.setTitle("内容推荐视频 " + videoId);
        video.setDescription("基于内容特征推荐");
        video.setDuration((int) content.duration + "分钟");
        video.setAgeRange(content.ageRange);
        video.setTags(new ArrayList<>(content.tags));
        video.setSpecialNeedsFocus(content.specialNeeds);
        video.setSource(content.source);
        video.setRelevanceScore(score);
        
        return video;
    }
    
    /**
     * 创建模拟视频资源
     */
    private VideoResource createMockVideoResource(String videoId, double score) {
        VideoResource video = new VideoResource();
        video.setId(videoId);
        video.setTitle("内容推荐视频 " + videoId);
        video.setDescription("基于内容特征推荐");
        video.setDuration("8分钟");
        video.setAgeRange("5-8岁");
        video.setTags(Arrays.asList("内容过滤", "个性化推荐"));
        video.setSource("Content-Based Filtering");
        video.setRelevanceScore(score);
        return video;
    }
    
    /**
     * 初始化示例内容数据
     */
    public void initializeSampleData() {
        // 数学内容
        VideoContent math1 = new VideoContent("math_001");
        math1.tags.addAll(Arrays.asList("数学", "数字", "基础"));
        math1.keywords.addAll(Arrays.asList("counting", "numbers", "basic"));
        math1.specialNeeds = "学习障碍";
        math1.ageRange = "5-7岁";
        math1.source = "YouTube";
        math1.duration = 10;
        videoContentMap.put("math_001", math1);
        
        // 科学内容
        VideoContent science1 = new VideoContent("science_001");
        science1.tags.addAll(Arrays.asList("科学", "探索", "实验"));
        science1.keywords.addAll(Arrays.asList("experiment", "discovery", "science"));
        science1.specialNeeds = "自闭症";
        science1.ageRange = "6-8岁";
        science1.source = "PBS Kids";
        science1.duration = 12;
        videoContentMap.put("science_001", science1);
        
        // 社交内容
        VideoContent social1 = new VideoContent("social_001");
        social1.tags.addAll(Arrays.asList("社交", "情绪", "互动"));
        social1.keywords.addAll(Arrays.asList("social", "emotional", "interaction"));
        social1.specialNeeds = "社交障碍";
        social1.ageRange = "4-6岁";
        social1.source = "Sesame Street";
        social1.duration = 8;
        videoContentMap.put("social_001", social1);
    }
}
