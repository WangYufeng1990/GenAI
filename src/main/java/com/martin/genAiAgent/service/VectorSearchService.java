package com.martin.genAiAgent.service;

import org.springframework.stereotype.Service;
import com.martin.genAiAgent.model.VideoResource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量搜索服务 - 实现语义搜索和相似度匹配
 */
@Service
public class VectorSearchService {
    
    // 模拟向量数据库，实际应该使用专业的向量数据库如 Pinecone, Weaviate 等
    private final Map<String, float[]> videoVectors = new HashMap<>();
    private final Map<String, VideoResource> videoDatabase = new HashMap<>();
    
    public VectorSearchService() {
        initializeMockData();
    }
    
    /**
     * 语义搜索视频
     */
    public List<VideoResource> semanticSearch(String query, String specialNeeds, String ageRange, int maxResults) {
        // 将查询转换为向量（简化实现）
        float[] queryVector = textToVector(query);
        
        // 计算相似度并排序
        List<Map.Entry<String, Double>> similarities = new ArrayList<>();
        
        for (Map.Entry<String, float[]> entry : videoVectors.entrySet()) {
            String videoId = entry.getKey();
            float[] videoVector = entry.getValue();
            
            // 计算余弦相似度
            double similarity = cosineSimilarity(queryVector, videoVector);
            
            // 应用特殊需求和年龄过滤
            VideoResource video = videoDatabase.get(videoId);
            if (isVideoSuitable(video, specialNeeds, ageRange)) {
                similarities.add(new AbstractMap.SimpleEntry<>(videoId, similarity));
            }
        }
        
        // 按相似度排序
        similarities.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        
        // 返回前N个结果
        return similarities.stream()
                .limit(maxResults)
                .map(entry -> videoDatabase.get(entry.getKey()))
                .collect(Collectors.toList());
    }
    
    /**
     * 添加视频到向量数据库
     */
    public void addVideo(VideoResource video) {
        String videoId = video.getId();
        float[] vector = textToVector(video.getTitle() + " " + video.getDescription() + " " + String.join(" ", video.getTags()));
        
        videoVectors.put(videoId, vector);
        videoDatabase.put(videoId, video);
    }
    
    /**
     * 文本转向量（简化实现）
     */
    private float[] textToVector(String text) {
        // 这是一个简化的TF-IDF实现
        // 实际应该使用专业的embedding模型如 OpenAI embeddings, BERT等
        
        String[] words = text.toLowerCase().split("\\s+");
        Map<String, Integer> wordCount = new HashMap<>();
        
        for (String word : words) {
            wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
        }
        
        // 创建固定长度的向量（128维）
        float[] vector = new float[128];
        int dimension = 0;
        
        for (Map.Entry<String, Integer> entry : wordCount.entrySet()) {
            if (dimension >= 128) break;
            
            // 简单的hash函数将词映射到向量维度
            int hash = Math.abs(entry.getKey().hashCode()) % 128;
            vector[hash] = entry.getValue();
            dimension++;
        }
        
        return vector;
    }
    
    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 检查视频是否适合用户
     */
    private boolean isVideoSuitable(VideoResource video, String specialNeeds, String ageRange) {
        // 检查特殊需求匹配
        if (!video.getSpecialNeedsFocus().contains(specialNeeds)) {
            return false;
        }
        
        // 检查年龄范围匹配
        if (!video.getAgeRange().contains(ageRange.substring(0, 1))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 初始化模拟数据
     */
    private void initializeMockData() {
        // 添加一些模拟的教育视频
        List<VideoResource> mockVideos = Arrays.asList(
            new VideoResource("vec_001", "颜色学习 - 自闭症友好版", 
                "通过缓慢的动画和重复学习颜色识别", "10分钟", "3-6岁",
                Arrays.asList("颜色识别", "视觉学习", "重复练习"), "自闭症",
                "VectorDB", "https://example.com/video1", "https://example.com/thumb1.jpg"),
                
            new VideoResource("vec_002", "数字1-10 - 多动症版", 
                "快节奏的数字学习，包含互动元素", "8分钟", "4-7岁",
                Arrays.asList("数字学习", "互动", "快节奏"), "多动症",
                "VectorDB", "https://example.com/video2", "https://example.com/thumb2.jpg"),
                
            new VideoResource("vec_003", "社交技能训练 - 情绪识别", 
                "帮助特殊需求儿童理解面部表情和情绪", "12分钟", "5-8岁",
                Arrays.asList("社交技能", "情绪识别", "面部表情"), "社交障碍",
                "VectorDB", "https://example.com/video3", "https://example.com/thumb3.jpg"),
                
            new VideoResource("vec_004", "字母学习 - 感觉统合版", 
                "结合触觉和视觉的字母学习体验", "15分钟", "3-6岁",
                Arrays.asList("字母学习", "感觉统合", "多感官"), "感觉统合障碍",
                "VectorDB", "https://example.com/video4", "https://example.com/thumb4.jpg"),
                
            new VideoResource("vec_005", "简单数学 - 步骤分解版", 
                "将数学概念分解为小步骤，适合学习障碍儿童", "10分钟", "6-9岁",
                Arrays.asList("数学", "步骤分解", "重复练习"), "学习障碍",
                "VectorDB", "https://example.com/video5", "https://example.com/thumb5.jpg")
        );
        
        for (VideoResource video : mockVideos) {
            addVideo(video);
        }
    }
    
    /**
     * 获取推荐解释
     */
    public String getRecommendationExplanation(String query, VideoResource video, double similarity) {
        return String.format("基于语义分析，'%s' 与 '%s' 的相似度为 %.2f。推荐理由：%s", 
                          query, video.getTitle(), similarity, getRecommendationReason(video));
    }
    
    private String getRecommendationReason(VideoResource video) {
        if (video.getTags().contains("视觉学习")) {
            return "视觉友好的内容，适合视觉型学习者";
        } else if (video.getTags().contains("互动")) {
            return "互动性强，能保持儿童注意力";
        } else if (video.getTags().contains("步骤分解")) {
            return "步骤清晰，便于理解和跟随";
        } else if (video.getTags().contains("重复练习")) {
            return "重复性练习有助于记忆巩固";
        }
        return "内容与学习目标高度匹配";
    }
}
