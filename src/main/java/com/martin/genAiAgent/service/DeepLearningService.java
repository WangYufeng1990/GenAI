package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 深度学习推荐服务
 */
@Service
public class DeepLearningService {
    
    private final Map<String, UserEmbedding> userEmbeddings = new ConcurrentHashMap<>();
    private final Map<String, VideoEmbedding> videoEmbeddings = new ConcurrentHashMap<>();
    private final NeuralNetworkModel neuralNetwork = new NeuralNetworkModel();
    
    /**
     * 用户嵌入向量
     */
    public static class UserEmbedding {
        String userId;
        double[] vector;
        Map<String, Double> preferences;
        String specialNeeds;
        String ageRange;
        List<String> learningHistory;
        
        public UserEmbedding(String userId, int dimension) {
            this.userId = userId;
            this.vector = new double[dimension];
            this.preferences = new HashMap<>();
            this.learningHistory = new ArrayList<>();
            Arrays.fill(this.vector, 0.1); // 初始化为小值
        }
    }
    
    /**
     * 视频嵌入向量
     */
    public static class VideoEmbedding {
        String videoId;
        double[] vector;
        Set<String> tags;
        String specialNeeds;
        String ageRange;
        String source;
        
        public VideoEmbedding(String videoId, int dimension) {
            this.videoId = videoId;
            this.vector = new double[dimension];
            this.tags = new HashSet<>();
            Arrays.fill(this.vector, Math.random() * 0.1); // 随机初始化
        }
    }
    
    /**
     * 神经网络模型
     */
    public static class NeuralNetworkModel {
        private final int inputSize = 64;
        private final int hiddenSize = 32;
        private final int outputSize = 1;
        
        private double[][] weights1 = new double[inputSize][hiddenSize];
        private double[][] weights2 = new double[hiddenSize][outputSize];
        private double[] bias1 = new double[hiddenSize];
        private double[] bias2 = new double[outputSize];
        
        public NeuralNetworkModel() {
            initializeWeights();
        }
        
        private void initializeWeights() {
            Random random = new Random();
            for (int i = 0; i < inputSize; i++) {
                for (int j = 0; j < hiddenSize; j++) {
                    weights1[i][j] = random.nextGaussian() * 0.1;
                }
            }
            for (int i = 0; i < hiddenSize; i++) {
                for (int j = 0; j < outputSize; j++) {
                    weights2[i][j] = random.nextGaussian() * 0.1;
                }
            }
        }
        
        public double predict(double[] input) {
            double[] hidden = new double[hiddenSize];
            
            // 前向传播 - 隐藏层
            for (int i = 0; i < hiddenSize; i++) {
                double sum = bias1[i];
                for (int j = 0; j < inputSize; j++) {
                    sum += input[j] * weights1[j][i];
                }
                hidden[i] = Math.tanh(sum); // tanh激活函数
            }
            
            // 前向传播 - 输出层
            double output = bias2[0];
            for (int i = 0; i < hiddenSize; i++) {
                output += hidden[i] * weights2[i][0];
            }
            
            return 1.0 / (1.0 + Math.exp(-output)); // sigmoid激活函数
        }
        
        public void update(double[] input, double target, double learningRate) {
            double[] hidden = new double[hiddenSize];
            
            // 前向传播
            for (int i = 0; i < hiddenSize; i++) {
                double sum = bias1[i];
                for (int j = 0; j < inputSize; j++) {
                    sum += input[j] * weights1[j][i];
                }
                hidden[i] = Math.tanh(sum);
            }
            
            double output = bias2[0];
            for (int i = 0; i < hiddenSize; i++) {
                output += hidden[i] * weights2[i][0];
            }
            output = 1.0 / (1.0 + Math.exp(-output)); // sigmoid activation function
            
            // 反向传播
            double outputError = target - output;
            double outputDelta = outputError * output * (1 - output);
            
            // 更新权重2
            for (int i = 0; i < hiddenSize; i++) {
                weights2[i][0] += learningRate * outputDelta * hidden[i];
            }
            bias2[0] += learningRate * outputDelta;
            
            // 更新权重1
            for (int i = 0; i < inputSize; i++) {
                for (int j = 0; j < hiddenSize; j++) {
                    double hiddenError = outputDelta * weights2[j][0];
                    double hiddenDelta = hiddenError * (1 - hidden[j] * hidden[j]);
                    weights1[i][j] += learningRate * hiddenDelta * input[i];
                }
            }
            
            for (int i = 0; i < hiddenSize; i++) {
                double hiddenError = outputDelta * weights2[i][0];
                double hiddenDelta = hiddenError * (1 - hidden[i] * hidden[i]);
                bias1[i] += learningRate * hiddenDelta;
            }
        }
    }
    
    /**
     * 深度学习推荐
     */
    public Flux<VideoResource> deepLearningRecommendation(String userId, String specialNeeds, String ageRange,
                                                         List<String> learningGoals, int maxResults) {
        return Mono.fromCallable(() -> {
            // 1. 获取或创建用户嵌入
            UserEmbedding userEmbedding = getOrCreateUserEmbedding(userId, specialNeeds, ageRange);
            
            // 2. 计算所有视频的预测分数
            Map<String, Double> predictions = new HashMap<>();
            
            for (VideoEmbedding videoEmbedding : videoEmbeddings.values()) {
                double[] combinedInput = combineEmbeddings(userEmbedding, videoEmbedding, learningGoals);
                double prediction = neuralNetwork.predict(combinedInput);
                predictions.put(videoEmbedding.videoId, prediction);
            }
            
            // 3. 排序并返回推荐
            return predictions.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(maxResults)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    ));
        })
        .flatMapMany(scoreMap -> {
            return Flux.fromIterable(scoreMap.entrySet())
                    .map(entry -> createVideoResourceFromEmbedding(entry.getKey(), entry.getValue()));
        });
    }
    
    /**
     * 更新深度学习模型
     */
    public Mono<Void> updateModel(String userId, String videoId, double rating, long watchTime, boolean completed) {
        return Mono.fromRunnable(() -> {
            UserEmbedding userEmbedding = getOrCreateUserEmbedding(userId, null, null);
            VideoEmbedding videoEmbedding = getOrCreateVideoEmbedding(videoId);
            
            // 构建训练输入
            double[] input = combineEmbeddings(userEmbedding, videoEmbedding, null);
            
            // 计算目标值（基于评分、观看时长和完成率）
            double target = calculateTargetValue(rating, watchTime, completed);
            
            // 训练神经网络
            neuralNetwork.update(input, target, 0.01); // 学习率0.01
            
            // 更新嵌入向量
            updateEmbeddings(userEmbedding, videoEmbedding, target);
        });
    }
    
    /**
     * 获取推荐解释
     */
    public Mono<String> getExplanation(String userId, String videoId) {
        return Mono.fromCallable(() -> {
            UserEmbedding userEmbedding = userEmbeddings.get(userId);
            VideoEmbedding videoEmbedding = videoEmbeddings.get(videoId);
            
            if (userEmbedding == null || videoEmbedding == null) {
                return "基于深度学习模型的推荐";
            }
            
            double similarity = calculateEmbeddingSimilarity(userEmbedding.vector, videoEmbedding.vector);
            
            return String.format("基于深度学习分析，用户与视频的匹配度为%.2f，" +
                               "考虑了您的学习历史和偏好模式", similarity);
        });
    }
    
    /**
     * 获取或创建用户嵌入
     */
    private UserEmbedding getOrCreateUserEmbedding(String userId, String specialNeeds, String ageRange) {
        return userEmbeddings.computeIfAbsent(userId, id -> {
            UserEmbedding embedding = new UserEmbedding(id, 64);
            if (specialNeeds != null) embedding.specialNeeds = specialNeeds;
            if (ageRange != null) embedding.ageRange = ageRange;
            return embedding;
        });
    }
    
    /**
     * 获取或创建视频嵌入
     */
    private VideoEmbedding getOrCreateVideoEmbedding(String videoId) {
        return videoEmbeddings.computeIfAbsent(videoId, id -> new VideoEmbedding(id, 64));
    }
    
    /**
     * 合并嵌入向量
     */
    private double[] combineEmbeddings(UserEmbedding userEmbedding, VideoEmbedding videoEmbedding, 
                                      List<String> learningGoals) {
        double[] combined = new double[64];
        
        // 用户嵌入（前32维）
        System.arraycopy(userEmbedding.vector, 0, combined, 0, 32);
        
        // 视频嵌入（后32维）
        System.arraycopy(videoEmbedding.vector, 0, combined, 32, 32);
        
        // 添加学习目标特征
        if (learningGoals != null) {
            for (String goal : learningGoals) {
                int index = Math.abs(goal.hashCode()) % 64;
                combined[index] += 0.1;
            }
        }
        
        return combined;
    }
    
    /**
     * 计算目标值
     */
    private double calculateTargetValue(double rating, long watchTime, boolean completed) {
        double target = rating / 5.0; // 标准化评分
        
        // 观看时长权重
        double watchTimeScore = Math.min(watchTime / 600.0, 1.0); // 标准化到10分钟
        target = target * 0.7 + watchTimeScore * 0.3;
        
        // 完成率权重
        if (completed) {
            target = Math.min(target + 0.1, 1.0);
        }
        
        return target;
    }
    
    /**
     * 更新嵌入向量
     */
    private void updateEmbeddings(UserEmbedding userEmbedding, VideoEmbedding videoEmbedding, double target) {
        // 简单的嵌入更新策略
        double learningRate = 0.01;
        
        for (int i = 0; i < userEmbedding.vector.length; i++) {
            userEmbedding.vector[i] += learningRate * (target - 0.5) * 0.1;
        }
        
        for (int i = 0; i < videoEmbedding.vector.length; i++) {
            videoEmbedding.vector[i] += learningRate * (target - 0.5) * 0.1;
        }
    }
    
    /**
     * 计算嵌入相似度
     */
    private double calculateEmbeddingSimilarity(double[] vector1, double[] vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < Math.min(vector1.length, vector2.length); i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * 从嵌入创建视频资源
     */
    private VideoResource createVideoResourceFromEmbedding(String videoId, double score) {
        VideoEmbedding embedding = videoEmbeddings.get(videoId);
        if (embedding == null) {
            return createMockVideoResource(videoId, score);
        }
        
        VideoResource video = new VideoResource();
        video.setId(videoId);
        video.setTitle("深度学习推荐视频 " + videoId);
        video.setDescription("基于神经网络模型推荐");
        video.setDuration("12分钟");
        video.setAgeRange(embedding.ageRange != null ? embedding.ageRange : "5-8岁");
        video.setTags(new ArrayList<>(embedding.tags));
        video.setSpecialNeedsFocus(embedding.specialNeeds);
        video.setSource(embedding.source != null ? embedding.source : "Deep Learning");
        video.setRelevanceScore(score);
        
        return video;
    }
    
    /**
     * 创建模拟视频资源
     */
    private VideoResource createMockVideoResource(String videoId, double score) {
        VideoResource video = new VideoResource();
        video.setId(videoId);
        video.setTitle("深度学习推荐视频 " + videoId);
        video.setDescription("基于神经网络模型推荐");
        video.setDuration("15分钟");
        video.setAgeRange("5-8岁");
        video.setTags(Arrays.asList("深度学习", "神经网络", "个性化推荐"));
        video.setSource("Deep Learning Service");
        video.setRelevanceScore(score);
        return video;
    }
    
    /**
     * 初始化示例数据
     */
    public void initializeSampleData() {
        // 创建示例视频嵌入
        VideoEmbedding video1 = new VideoEmbedding("dl_001", 64);
        video1.tags.addAll(Arrays.asList("数学", "逻辑", "认知"));
        video1.specialNeeds = "自闭症";
        video1.ageRange = "6-8岁";
        video1.source = "YouTube";
        videoEmbeddings.put("dl_001", video1);
        
        VideoEmbedding video2 = new VideoEmbedding("dl_002", 64);
        video2.tags.addAll(Arrays.asList("社交", "情绪", "互动"));
        video2.specialNeeds = "社交障碍";
        video2.ageRange = "4-6岁";
        video2.source = "PBS Kids";
        videoEmbeddings.put("dl_002", video2);
    }
}
