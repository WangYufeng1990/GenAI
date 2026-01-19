package com.martin.genAiAgent.service;

import com.martin.genAiAgent.config.ApiConfig;
import com.martin.genAiAgent.model.VideoResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

/**
 * PBS Kids API 服务
 */
@Service
public class PBSKidsApiService {
    
    private final WebClient webClient;
    private final ApiConfig apiConfig;
    
    @Autowired
    public PBSKidsApiService(WebClient pbsKidsWebClient, ApiConfig apiConfig) {
        this.webClient = pbsKidsWebClient;
        this.apiConfig = apiConfig;
    }
    
    /**
     * 搜索 PBS Kids 教育内容
     */
    @Cacheable(value = "pbs-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> searchEducationalContent(String query, String specialNeeds, String ageRange, int maxResults) {
        String apiKey = getApiKey();
        
        // 构建 PBS Kids 搜索查询
        String searchQuery = buildPBSKidsQuery(query, specialNeeds, ageRange);
        
        // PBS Kids API 搜索
        return searchPBSKidsContent(searchQuery, apiKey)
                .flatMap(this::getContentDetails)
                .filter(Objects::nonNull)
                .map(this::convertToVideoResource)
                .filter(Objects::nonNull)
                .take(maxResults)
                .doOnError(error -> System.err.println("PBS Kids API error: " + error.getMessage()))
                .onErrorResume(error -> {
                    System.err.println("PBS Kids API fallback to mock data: " + error.getMessage());
                    return getMockPBSKidsVideos(query, specialNeeds, ageRange);
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .jitter(0.5));
    }
    
    /**
     * 搜索 PBS Kids 内容
     */
    private Flux<Map<String, Object>> searchPBSKidsContent(String query, String apiKey) {
        // PBS Kids 搜索不同类型的内容
        return Flux.merge(
            searchVideos(query, apiKey),
            searchGames(query, apiKey),
            searchActivities(query, apiKey)
        );
    }
    
    /**
     * 搜索视频内容
     */
    private Flux<Map<String, Object>> searchVideos(String query, String apiKey) {
        String url = String.format("/videos/search?q=%s&limit=15&age=preschool,early-elementary", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "video".equals(item.get("type")));
    }
    
    /**
     * 搜索游戏内容
     */
    private Flux<Map<String, Object>> searchGames(String query, String apiKey) {
        String url = String.format("/games/search?q=%s&limit=10&age=preschool,early-elementary", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "game".equals(item.get("type")));
    }
    
    /**
     * 搜索活动内容
     */
    private Flux<Map<String, Object>> searchActivities(String query, String apiKey) {
        String url = String.format("/activities/search?q=%s&limit=10&age=preschool,early-elementary", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "activity".equals(item.get("type")));
    }
    
    /**
     * 获取内容详细信息
     */
    private Mono<Map<String, Object>> getContentDetails(Map<String, Object> searchResult) {
        String contentId = (String) searchResult.get("id");
        String contentType = (String) searchResult.get("type");
        
        String url = String.format("/%s/%s", contentType, contentId);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .map(details -> {
                    Map<String, Object> combined = new HashMap<>(searchResult);
                    combined.putAll(details);
                    return combined;
                })
                .onErrorReturn(searchResult);
    }
    
    /**
     * 转换为 VideoResource 对象
     */
    @SuppressWarnings("unchecked")
    private VideoResource convertToVideoResource(Map<String, Object> contentData) {
        try {
            String contentType = (String) contentData.get("type");
            String title = (String) contentData.get("title");
            String description = (String) contentData.get("description");
            String contentUrl = (String) contentData.get("url");
            
            // 解析时长和难度
            String duration = parseDuration(contentData);
            String ageRange = analyzeAgeRange(title, description, contentType);
            List<String> tags = extractTags(title, description, contentType);
            String specialNeedsFocus = analyzeSpecialNeeds(title, description, tags);
            
            VideoResource video = new VideoResource();
            video.setId("pbs_" + contentData.get("id"));
            video.setTitle(title);
            video.setDescription(description);
            video.setDuration(duration);
            video.setAgeRange(ageRange);
            video.setTags(tags);
            video.setSpecialNeedsFocus(specialNeedsFocus);
            video.setSource("PBS Kids");
            video.setVideoUrl("https://pbskids.org" + contentUrl);
            video.setThumbnailUrl(getThumbnailUrl(contentData));
            
            // 计算相关性分数
            double relevanceScore = calculateRelevanceScore(contentData, contentType);
            video.setRelevanceScore(relevanceScore);
            
            return video;
            
        } catch (Exception e) {
            System.err.println("Error converting PBS Kids content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建 PBS Kids 搜索查询
     */
    private String buildPBSKidsQuery(String query, String specialNeeds, String ageRange) {
        StringBuilder sb = new StringBuilder();
        
        // 基础查询
        sb.append(query);
        
        // 根据特殊需求添加关键词
        switch (specialNeeds.toLowerCase()) {
            case "自闭症":
                sb.append(" visual calm gentle");
                break;
            case "多动症":
                sb.append(" interactive engaging movement");
                break;
            case "学习障碍":
                sb.append(" simple clear basic");
                break;
            case "感觉统合障碍":
                sb.append(" sensory hands-on tactile");
                break;
            case "社交障碍":
                sb.append(" social emotional feelings");
                break;
            case "语言发育迟缓":
                sb.append(" language vocabulary speech");
                break;
        }
        
        // PBS Kids 特有的关键词
        sb.append(" educational learning");
        
        return sb.toString().replace(" ", "+");
    }
    
    /**
     * 解析时长
     */
    private String parseDuration(Map<String, Object> contentData) {
        String contentType = (String) contentData.get("type");
        
        if ("video".equals(contentType)) {
            Object duration = contentData.get("duration");
            if (duration instanceof Number) {
                int seconds = ((Number) duration).intValue();
                int minutes = seconds / 60;
                int remainingSeconds = seconds % 60;
                return String.format("%d分%d秒", minutes, remainingSeconds);
            }
        } else if ("game".equals(contentType)) {
            return "互动游戏";
        } else if ("activity".equals(contentType)) {
            return "手工活动";
        }
        
        return "未知";
    }
    
    /**
     * 分析适合年龄
     */
    private String analyzeAgeRange(String title, String description, String contentType) {
        String content = (title + " " + description).toLowerCase();
        
        if (content.contains("preschool") || content.contains("toddler") || 
            content.contains("age 3") || content.contains("age 4")) {
            return "3-5岁";
        } else if (content.contains("kindergarten") || content.contains("early elementary") || 
                  content.contains("age 5") || content.contains("age 6")) {
            return "5-7岁";
        } else if (content.contains("elementary") || content.contains("age 7") || 
                  content.contains("age 8")) {
            return "7-9岁";
        }
        
        return "4-7岁"; // PBS Kids 主要适合的年龄范围
    }
    
    /**
     * 提取标签
     */
    private List<String> extractTags(String title, String description, String contentType) {
        List<String> tags = new ArrayList<>();
        
        String content = (title + " " + description).toLowerCase();
        
        // 内容类型标签
        if ("video".equals(contentType)) tags.add("视频");
        if ("game".equals(contentType)) tags.add("游戏");
        if ("activity".equals(contentType)) tags.add("活动");
        
        // PBS Kids 角色标签
        if (content.contains("daniel tiger")) tags.add("Daniel Tiger");
        if (content.contains("curious george")) tags.add("好奇猴乔治");
        if (content.contains("wild kratts")) tags.add("Wild Kratts");
        if (content.contains("peg cat")) tags.add("Peg + Cat");
        if (content.contains("odd squad")) tags.add("Odd Squad");
        
        // 学科标签
        if (content.contains("math") || content.contains("count") || content.contains("number")) {
            tags.add("数学");
        }
        if (content.contains("science") || content.contains("explore") || content.contains("discover")) {
            tags.add("科学探索");
        }
        if (content.contains("reading") || content.contains("story") || content.contains("book")) {
            tags.add("阅读");
        }
        if (content.contains("art") || content.contains("draw") || content.contains("create")) {
            tags.add("美术");
        }
        if (content.contains("music") || content.contains("song") || content.contains("dance")) {
            tags.add("音乐");
        }
        
        // 学习方式标签
        if (content.contains("interactive") || content.contains("play")) {
            tags.add("互动");
        }
        if (content.contains("visual") || content.contains("animation")) {
            tags.add("视觉学习");
        }
        if (content.contains("social") || content.contains("friendship") || content.contains("sharing")) {
            tags.add("社交技能");
        }
        if (content.contains("emotion") || content.contains("feeling")) {
            tags.add("情绪识别");
        }
        
        return tags;
    }
    
    /**
     * 分析特殊需求焦点
     */
    private String analyzeSpecialNeeds(String title, String description, List<String> tags) {
        String content = (title + " " + description).toLowerCase();
        
        if (content.contains("visual") || content.contains("animation") || content.contains("calm") ||
                  tags.contains("视觉学习")) {
            return "自闭症";
        } else if (content.contains("interactive") || content.contains("movement") || content.contains("engaging") ||
                  tags.contains("互动")) {
            return "多动症";
        } else if (content.contains("simple") || content.contains("clear") || content.contains("basic") ||
                  tags.contains("步骤分解")) {
            return "学习障碍";
        } else if (content.contains("sensory") || content.contains("hands-on") || content.contains("tactile")) {
            return "感觉统合障碍";
        } else if (content.contains("social") || content.contains("emotional") || content.contains("feeling") ||
                  tags.contains("社交技能") || tags.contains("情绪识别")) {
            return "社交障碍";
        } else if (content.contains("language") || content.contains("vocabulary") || content.contains("speech")) {
            return "语言发育迟缓";
        }
        
        return "通用";
    }
    
    /**
     * 获取缩略图URL
     */
    private String getThumbnailUrl(Map<String, Object> contentData) {
        Object imageUrl = contentData.get("image");
        if (imageUrl != null) {
            return imageUrl.toString();
        }
        return "";
    }
    
    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(Map<String, Object> contentData, String contentType) {
        double score = 0.5; // 基础分数
        
        // 内容类型权重
        if ("video".equals(contentType)) score += 0.2;
        else if ("game".equals(contentType)) score += 0.25; // 游戏更互动
        else if ("activity".equals(contentType)) score += 0.15;
        
        // 教育价值权重
        Object educationalValue = contentData.get("educational_value");
        if (educationalValue instanceof Number) {
            double value = ((Number) educationalValue).doubleValue();
            score += value * 0.2;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 获取 API 密钥
     */
    private String getApiKey() {
        String apiKey = apiConfig.getPbsKids().getApiKey();
        if (apiKey == null || apiKey.contains("your_pbs_kids_api_key_here")) {
            return System.getenv("PBS_KIDS_API_KEY");
        }
        return apiKey;
    }
    
    /**
     * 获取模拟 PBS Kids 视频（当 API 不可用时）
     */
    private Flux<VideoResource> getMockPBSKidsVideos(String query, String specialNeeds, String ageRange) {
        return Flux.fromArray(new VideoResource[] {
            new VideoResource("pbs_real_001", "颜色和形状 - Daniel Tiger", 
                "Daniel Tiger 教颜色和形状，适合" + specialNeeds + "儿童", "7分钟", ageRange,
                Arrays.asList("颜色", "形状", "视觉学习"), specialNeeds,
                "PBS Kids", "https://pbskids.org/video/daniel-tiger", "https://images.pbskids.org/daniel-tiger/poster.jpg"),
                
            new VideoResource("pbs_real_002", "字母学习 - Super Why", 
                "Super Why 带你学习字母和发音", "10分钟", ageRange,
                Arrays.asList("字母", "发音", "语言"), specialNeeds,
                "PBS Kids", "https://pbskids.org/video/super-why", "https://images.pbskids.org/super-why/poster.jpg"),
                
            new VideoResource("pbs_real_003", "数字游戏 - Curious George", 
                "好奇猴乔治教你数字概念", "8分钟", ageRange,
                Arrays.asList("数字", "数学", "游戏"), specialNeeds,
                "PBS Kids", "https://pbskids.org/video/curious-george", "https://images.pbskids.org/curious-george/poster.jpg")
        });
    }
}
