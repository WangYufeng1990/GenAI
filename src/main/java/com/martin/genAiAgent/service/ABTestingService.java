package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.VideoResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A/B测试服务
 */
@Service
public class ABTestingService {
    
    private final Map<String, ABTest> activeTests = new ConcurrentHashMap<>();
    private final Map<String, List<TestResult>> testResults = new ConcurrentHashMap<>();
    private final Map<String, String> userAssignments = new ConcurrentHashMap<>();
    
    /**
     * A/B测试配置
     */
    public static class ABTest {
        String testId;
        String testName;
        String description;
        Map<String, TestVariant> variants;
        LocalDateTime startTime;
        LocalDateTime endTime;
        int totalUsers;
        double trafficSplit;
        TestStatus status;
        
        public ABTest(String testId, String testName) {
            this.testId = testId;
            this.testName = testName;
            this.variants = new HashMap<>();
            this.status = TestStatus.ACTIVE;
            this.startTime = LocalDateTime.now();
            this.trafficSplit = 0.5; // 默认50/50分配
        }
    }
    
    /**
     * 测试变体
     */
    public static class TestVariant {
        String variantId;
        String name;
        String description;
        Map<String, Object> configuration;
        int userCount;
        double conversionRate;
        double averageRating;
        double averageWatchTime;
        
        public TestVariant(String variantId, String name) {
            this.variantId = variantId;
            this.name = name;
            this.configuration = new HashMap<>();
        }
    }
    
    /**
     * 测试结果
     */
    public static class TestResult {
        String userId;
        String testId;
        String variantId;
        String action; // click, watch, rate, complete
        double value; // rating, watch_time, etc.
        LocalDateTime timestamp;
        
        public TestResult(String userId, String testId, String variantId, String action, double value) {
            this.userId = userId;
            this.testId = testId;
            this.variantId = variantId;
            this.action = action;
            this.value = value;
            this.timestamp = LocalDateTime.now();
        }
    }
    
    /**
     * 测试状态
     */
    public enum TestStatus {
        ACTIVE, COMPLETED, PAUSED
    }
    
    /**
     * 创建新的A/B测试
     */
    public Mono<ABTest> createTest(String testId, String testName, String description) {
        return Mono.fromCallable(() -> {
            ABTest test = new ABTest(testId, testName);
            test.description = description;
            
            // 创建默认变体
            TestVariant controlVariant = new TestVariant("control", "对照组");
            controlVariant.description = "当前推荐算法";
            
            TestVariant testVariant = new TestVariant("test", "测试组");
            testVariant.description = "新推荐算法";
            
            test.variants.put("control", controlVariant);
            test.variants.put("test", testVariant);
            
            activeTests.put(testId, test);
            testResults.put(testId, new ArrayList<>());
            
            return test;
        });
    }
    
    /**
     * 分配用户到测试组
     */
    public Mono<String> assignUserToVariant(String userId, String testId) {
        return Mono.fromCallable(() -> {
            String cacheKey = testId + "_" + userId;
            if (userAssignments.containsKey(cacheKey)) {
                return userAssignments.get(cacheKey);
            }
            
            ABTest test = activeTests.get(testId);
            if (test == null || test.status != TestStatus.ACTIVE) {
                return "control";
            }
            
            // 基于用户ID哈希分配
            int hash = Math.abs(userId.hashCode());
            String variant = hash % 100 < (test.trafficSplit * 100) ? "test" : "control";
            
            userAssignments.put(cacheKey, variant);
            test.variants.get(variant).userCount++;
            test.totalUsers++;
            
            return variant;
        });
    }
    
    /**
     * 获取推荐结果（带A/B测试）
     */
    public Flux<VideoResource> getRecommendationsWithABTest(String userId, String testId,
                                                      String specialNeeds, String ageRange,
                                                      List<String> learningGoals, int maxResults) {
        return assignUserToVariant(userId, testId)
                .flatMapMany(variant -> {
                    switch (variant) {
                        case "control":
                            return getControlRecommendations(userId, specialNeeds, ageRange, learningGoals, maxResults);
                        case "test":
                            return getTestRecommendations(userId, specialNeeds, ageRange, learningGoals, maxResults);
                        default:
                            return getControlRecommendations(userId, specialNeeds, ageRange, learningGoals, maxResults);
                    }
                })
                .doOnNext(video -> {
                    String variant = userAssignments.get(testId + "_" + userId);
                    recordTestResult(userId, testId, variant, "recommend", video.getRelevanceScore());
                });
    }
    
    /**
     * 对照组推荐
     */
    private Flux<VideoResource> getControlRecommendations(String userId, String specialNeeds, String ageRange,
                                                   List<String> learningGoals, int maxResults) {
        // 使用传统推荐算法
        VideoResource controlVideo1 = new VideoResource();
        controlVideo1.setId("control_001");
        controlVideo1.setTitle("传统推荐视频1");
        controlVideo1.setDescription("对照组推荐内容");
        controlVideo1.setDuration("10分钟");
        controlVideo1.setAgeRange("5-8岁");
        controlVideo1.setTags(Arrays.asList("传统", "推荐"));
        controlVideo1.setSpecialNeedsFocus("通用");
        controlVideo1.setSource("Control Algorithm");
        controlVideo1.setRelevanceScore(0.8);
        
        VideoResource controlVideo2 = new VideoResource();
        controlVideo2.setId("control_002");
        controlVideo2.setTitle("传统推荐视频2");
        controlVideo2.setDescription("对照组推荐内容");
        controlVideo2.setDuration("8分钟");
        controlVideo2.setAgeRange("4-7岁");
        controlVideo2.setTags(Arrays.asList("传统", "推荐"));
        controlVideo2.setSpecialNeedsFocus("通用");
        controlVideo2.setSource("Control Algorithm");
        controlVideo2.setRelevanceScore(0.7);
        
        return Flux.just(controlVideo1, controlVideo2);
    }
    
    /**
     * 测试组推荐
     */
    private Flux<VideoResource> getTestRecommendations(String userId, String specialNeeds, String ageRange,
                                                 List<String> learningGoals, int maxResults) {
        // 使用新的推荐算法
        VideoResource testVideo1 = new VideoResource();
        testVideo1.setId("test_001");
        testVideo1.setTitle("新算法推荐视频1");
        testVideo1.setDescription("测试组推荐内容");
        testVideo1.setDuration("12分钟");
        testVideo1.setAgeRange("5-8岁");
        testVideo1.setTags(Arrays.asList("新算法", "机器学习"));
        testVideo1.setSpecialNeedsFocus("通用");
        testVideo1.setSource("Test Algorithm");
        testVideo1.setRelevanceScore(0.9);
        
        VideoResource testVideo2 = new VideoResource();
        testVideo2.setId("test_002");
        testVideo2.setTitle("新算法推荐视频2");
        testVideo2.setDescription("测试组推荐内容");
        testVideo2.setDuration("9分钟");
        testVideo2.setAgeRange("4-7岁");
        testVideo2.setTags(Arrays.asList("新算法", "深度学习"));
        testVideo2.setSpecialNeedsFocus("通用");
        testVideo2.setSource("Test Algorithm");
        testVideo2.setRelevanceScore(0.85);
        
        return Flux.just(testVideo1, testVideo2);
    }
    
    /**
     * 记录测试结果
     */
    public Mono<Void> recordTestResult(String userId, String testId, String variantId, String action, double value) {
        return Mono.fromRunnable(() -> {
            TestResult result = new TestResult(userId, testId, variantId, action, value);
            testResults.computeIfAbsent(testId, k -> new ArrayList<>()).add(result);
        });
    }
    
    /**
     * 记录用户交互
     */
    public Mono<Void> recordUserInteraction(String userId, String testId, String videoId, 
                                       String action, double value) {
        return assignUserToVariant(userId, testId)
                .flatMap(variant -> recordTestResult(userId, testId, variant, action, value));
    }
    
    /**
     * 获取测试统计
     */
    public Mono<Map<String, Object>> getTestStatistics(String testId) {
        return Mono.fromCallable(() -> {
            ABTest test = activeTests.get(testId);
            if (test == null) {
                return Collections.emptyMap();
            }
            
            List<TestResult> results = testResults.getOrDefault(testId, Collections.emptyList());
            Map<String, Object> stats = new HashMap<>();
            
            // 基础统计
            stats.put("testId", test.testId);
            stats.put("testName", test.testName);
            stats.put("totalUsers", test.totalUsers);
            stats.put("status", test.status);
            
            // 变体统计
            Map<String, Object> variantStats = new HashMap<>();
            for (Map.Entry<String, TestVariant> entry : test.variants.entrySet()) {
                String variantId = entry.getKey();
                TestVariant variant = entry.getValue();
                
                List<TestResult> variantResults = results.stream()
                        .filter(r -> r.variantId.equals(variantId))
                        .collect(Collectors.toList());
                
                Map<String, Object> varStat = new HashMap<>();
                varStat.put("userCount", variant.userCount);
                varStat.put("totalInteractions", variantResults.size());
                
                // 计算转化率
                long clicks = variantResults.stream()
                        .filter(r -> "click".equals(r.action))
                        .count();
                double conversionRate = variant.userCount > 0 ? (double) clicks / variant.userCount : 0.0;
                varStat.put("conversionRate", conversionRate);
                
                // 计算平均评分
                double avgRating = variantResults.stream()
                        .filter(r -> "rate".equals(r.action))
                        .mapToDouble(r -> r.value)
                        .average()
                        .orElse(0.0);
                varStat.put("averageRating", avgRating);
                
                // 计算平均观看时长
                double avgWatchTime = variantResults.stream()
                        .filter(r -> "watch".equals(r.action))
                        .mapToDouble(r -> r.value)
                        .average()
                        .orElse(0.0);
                varStat.put("averageWatchTime", avgWatchTime);
                
                variantStats.put(variantId, varStat);
            }
            stats.put("variants", variantStats);
            
            // 统计显著性
            double significance = calculateStatisticalSignificance(results);
            stats.put("statisticalSignificance", significance);
            
            return stats;
        });
    }
    
    /**
     * 计算统计显著性
     */
    private double calculateStatisticalSignificance(List<TestResult> results) {
        if (results.size() < 100) {
            return 0.0; // 样本量不足
        }
        
        Map<String, List<TestResult>> groupedResults = results.stream()
                .collect(Collectors.groupingBy(result -> result.variantId));
        
        if (groupedResults.size() < 2) {
            return 0.0;
        }
        
        // 简化的t检验计算
        List<Double> controlValues = groupedResults.get("control").stream()
                .filter(r -> "rate".equals(r.action))
                .map(r -> r.value)
                .collect(Collectors.toList());
        
        List<Double> testValues = groupedResults.get("test").stream()
                .filter(r -> "rate".equals(r.action))
                .map(r -> r.value)
                .collect(Collectors.toList());
        
        if (controlValues.size() < 10 || testValues.size() < 10) {
            return 0.0;
        }
        
        double controlMean = controlValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double testMean = testValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double controlVar = controlValues.stream()
                .mapToDouble(v -> Math.pow(v - controlMean, 2))
                .average().orElse(0.0);
        
        double testVar = testValues.stream()
                .mapToDouble(v -> Math.pow(v - testMean, 2))
                .average().orElse(0.0);
        
        double pooledStd = Math.sqrt((controlVar * (controlValues.size() - 1) + testVar * (testValues.size() - 1)) / 
                                 (controlValues.size() + testValues.size() - 2));
        
        double tStat = (testMean - controlMean) / (pooledStd * Math.sqrt(1.0 / controlValues.size() + 1.0 / testValues.size()));
        
        // 简化的p值计算
        double pValue = 2 * (1 - normalCDF(Math.abs(tStat)));
        
        return 1 - pValue; // 返回置信度
    }
    
    /**
     * 正态分布累积分布函数
     */
    private double normalCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }
    
    /**
     * 误差函数近似
     */
    private double erf(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double ans = 1 - t * Math.exp(-x*x - 1.26551223 +
                                    t * (1.00002368 +
                                    t * (0.37409196 + 
                                    t * (0.09678418 + 
                                    t * (-0.18628806 + 
                                    t * (0.27886807 + 
                                    t * (-1.13520398 + 
                                    t * (1.48851587 + 
                                    t * (-0.82215223 + 
                                    t * 0.17087277)))))))));
        return x >= 0 ? ans : -ans;
    }
    
    /**
     * 结束测试
     */
    public Mono<ABTest> concludeTest(String testId, String winningVariant) {
        return Mono.fromCallable(() -> {
            ABTest test = activeTests.get(testId);
            if (test != null) {
                test.status = TestStatus.COMPLETED;
                test.endTime = LocalDateTime.now();
                
                // 更新变体配置
                for (TestVariant variant : test.variants.values()) {
                    if (variant.variantId.equals(winningVariant)) {
                        variant.configuration.put("isWinner", true);
                    }
                }
            }
            return test;
        });
    }
    
    /**
     * 获取所有活跃测试
     */
    public Mono<List<ABTest>> getActiveTests() {
        return Mono.fromCallable(() -> {
            return activeTests.values().stream()
                    .filter(test -> test.status == TestStatus.ACTIVE)
                    .collect(Collectors.toList());
        });
    }
}
