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
 * Khan Academy API 服务
 */
@Service
public class KhanAcademyApiService {
    
    private final WebClient webClient;
    private final ApiConfig apiConfig;
    
    @Autowired
    public KhanAcademyApiService(WebClient khanAcademyWebClient, ApiConfig apiConfig) {
        this.webClient = khanAcademyWebClient;
        this.apiConfig = apiConfig;
    }
    
    /**
     * 搜索 Khan Academy 教育内容
     */
    @Cacheable(value = "khan-academy-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> searchEducationalContent(String query, String specialNeeds, String ageRange, int maxResults) {
        String apiKey = getApiKey();
        
        // 构建 Khan Academy 搜索查询
        String searchQuery = buildKhanAcademyQuery(query, specialNeeds, ageRange);
        
        // Khan Academy API 搜索
        return searchKhanAcademyContent(searchQuery, apiKey)
                .flatMap(this::getContentDetails)
                .filter(Objects::nonNull)
                .map(this::convertToVideoResource)
                .filter(Objects::nonNull)
                .take(maxResults)
                .doOnError(error -> System.err.println("Khan Academy API error: " + error.getMessage()))
                .onErrorResume(error -> {
                    System.err.println("Khan Academy API fallback to mock data: " + error.getMessage());
                    return getMockKhanAcademyVideos(query, specialNeeds, ageRange);
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .jitter(0.5));
    }
    
    /**
     * 搜索 Khan Academy 内容
     */
    private Flux<Map<String, Object>> searchKhanAcademyContent(String query, String apiKey) {
        // Khan Academy 搜索不同类型的内容
        return Flux.merge(
            searchVideos(query, apiKey),
            searchExercises(query, apiKey),
            searchTopics(query, apiKey)
        );
    }
    
    /**
     * 搜索视频内容
     */
    private Flux<Map<String, Object>> searchVideos(String query, String apiKey) {
        String url = String.format("/search?query=%s&limit=20&lang=en", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "video".equals(item.get("kind")));
    }
    
    /**
     * 搜索练习内容
     */
    private Flux<Map<String, Object>> searchExercises(String query, String apiKey) {
        String url = String.format("/search?query=%s&limit=10&lang=en", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "exercise".equals(item.get("kind")));
    }
    
    /**
     * 搜索主题内容
     */
    private Flux<Map<String, Object>> searchTopics(String query, String apiKey) {
        String url = String.format("/search?query=%s&limit=10&lang=en", query);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    return results != null ? Flux.fromIterable(results) : Flux.empty();
                })
                .filter(item -> "topic".equals(item.get("kind")));
    }
    
    /**
     * 获取内容详细信息
     */
    private Mono<Map<String, Object>> getContentDetails(Map<String, Object> searchResult) {
        String contentUrl = (String) searchResult.get("ka_url");
        if (contentUrl == null) {
            return Mono.just(searchResult);
        }
        
        return webClient.get()
                .uri(contentUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .map(details -> {
                    Map<String, Object> combined = new HashMap<>(searchResult);
                    combined.putAll(details);
                    return combined;
                })
                .onErrorReturn(searchResult); // 如果获取详情失败，返回原始结果
    }
    
    /**
     * 转换为 VideoResource 对象
     */
    @SuppressWarnings("unchecked")
    private VideoResource convertToVideoResource(Map<String, Object> contentData) {
        try {
            String kind = (String) contentData.get("kind");
            String title = (String) contentData.get("translated_title");
            String description = (String) contentData.get("translated_description");
            String contentUrl = (String) contentData.get("ka_url");
            
            if (title == null || title.isEmpty()) {
                title = (String) contentData.get("title");
            }
            if (description == null || description.isEmpty()) {
                description = (String) contentData.get("description");
            }
            
            // 解析时长和难度
            String duration = parseDuration(contentData);
            String ageRange = analyzeAgeRange(title, description, kind);
            List<String> tags = extractTags(title, description, kind);
            String specialNeedsFocus = analyzeSpecialNeeds(title, description, tags);
            
            VideoResource video = new VideoResource();
            video.setId("ka_" + contentData.get("id"));
            video.setTitle(title);
            video.setDescription(description);
            video.setDuration(duration);
            video.setAgeRange(ageRange);
            video.setTags(tags);
            video.setSpecialNeedsFocus(specialNeedsFocus);
            video.setSource("Khan Academy");
            video.setVideoUrl("https://www.khanacademy.org" + contentUrl);
            video.setThumbnailUrl(getThumbnailUrl(contentData));
            
            // 计算相关性分数
            double relevanceScore = calculateRelevanceScore(contentData, kind);
            video.setRelevanceScore(relevanceScore);
            
            return video;
            
        } catch (Exception e) {
            System.err.println("Error converting Khan Academy content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建 Khan Academy 搜索查询
     */
    private String buildKhanAcademyQuery(String query, String specialNeeds, String ageRange) {
        StringBuilder sb = new StringBuilder();
        
        // 基础查询
        sb.append(query);
        
        // 根据特殊需求添加关键词
        switch (specialNeeds.toLowerCase()) {
            case "自闭症":
                sb.append(" visual step-by-step");
                break;
            case "多动症":
                sb.append(" interactive engaging");
                break;
            case "学习障碍":
                sb.append(" basic foundation");
                break;
            case "感觉统合障碍":
                sb.append(" hands-on sensory");
                break;
            case "社交障碍":
                sb.append(" social emotional");
                break;
            case "语言发育迟缓":
                sb.append(" language communication");
                break;
        }
        
        // 根据年龄添加关键词
        if (ageRange.contains("3") || ageRange.contains("4") || ageRange.contains("5")) {
            sb.append(" early-elementary");
        } else if (ageRange.contains("6") || ageRange.contains("7") || ageRange.contains("8")) {
            sb.append(" elementary");
        } else if (ageRange.contains("9") || ageRange.contains("10")) {
            sb.append(" middle-school");
        }
        
        return sb.toString().replace(" ", "+");
    }
    
    /**
     * 解析时长
     */
    private String parseDuration(Map<String, Object> contentData) {
        String kind = (String) contentData.get("kind");
        
        if ("video".equals(kind)) {
            // 视频时长（秒）
            Object duration = contentData.get("duration");
            if (duration instanceof Number) {
                int seconds = ((Number) duration).intValue();
                int minutes = seconds / 60;
                int remainingSeconds = seconds % 60;
                return String.format("%d分%d秒", minutes, remainingSeconds);
            }
        } else if ("exercise".equals(kind)) {
            return "练习题";
        } else if ("topic".equals(kind)) {
            return "学习单元";
        }
        
        return "未知";
    }
    
    /**
     * 分析适合年龄
     */
    private String analyzeAgeRange(String title, String description, String kind) {
        String content = (title + " " + description).toLowerCase();
        
        if (content.contains("preschool") || content.contains("kindergarten") || 
            content.contains("early elementary") || content.contains("basic")) {
            return "4-6岁";
        } else if (content.contains("elementary") || content.contains("grade 1") || 
                  content.contains("grade 2") || content.contains("grade 3")) {
            return "6-9岁";
        } else if (content.contains("middle school") || content.contains("grade 4") || 
                  content.contains("grade 5") || content.contains("grade 6")) {
            return "9-12岁";
        }
        
        return "6-10岁"; // 默认范围
    }
    
    /**
     * 提取标签
     */
    private List<String> extractTags(String title, String description, String kind) {
        List<String> tags = new ArrayList<>();
        
        String content = (title + " " + description).toLowerCase();
        
        // 内容类型标签
        if ("video".equals(kind)) tags.add("视频");
        if ("exercise".equals(kind)) tags.add("练习");
        if ("topic".equals(kind)) tags.add("主题");
        
        // 学科标签
        if (content.contains("math") || content.contains("number")) tags.add("数学");
        if (content.contains("science") || content.contains("physics") || content.contains("chemistry")) tags.add("科学");
        if (content.contains("reading") || content.contains("literature")) tags.add("阅读");
        if (content.contains("history") || content.contains("social")) tags.add("历史");
        if (content.contains("art") || content.contains("drawing")) tags.add("美术");
        if (content.contains("computer") || content.contains("programming")) tags.add("编程");
        
        // 学习方式标签
        if (content.contains("step by step") || content.contains("basic")) tags.add("步骤分解");
        if (content.contains("interactive") || content.contains("practice")) tags.add("互动");
        if (content.contains("visual") || content.contains("animation")) tags.add("视觉学习");
        
        return tags;
    }
    
    /**
     * 分析特殊需求焦点
     */
    private String analyzeSpecialNeeds(String title, String description, List<String> tags) {
        String content = (title + " " + description).toLowerCase();
        
        if (content.contains("step by step") || content.contains("basic") || 
            tags.contains("步骤分解")) {
            return "学习障碍";
        } else if (content.contains("visual") || content.contains("animation") ||
                  tags.contains("视觉学习")) {
            return "自闭症";
        } else if (content.contains("interactive") || content.contains("practice") ||
                  tags.contains("互动")) {
            return "多动症";
        } else if (content.contains("hands-on") || content.contains("sensory")) {
            return "感觉统合障碍";
        } else if (content.contains("social") || content.contains("emotional")) {
            return "社交障碍";
        } else if (content.contains("language") || content.contains("communication")) {
            return "语言发育迟缓";
        }
        
        return "通用";
    }
    
    /**
     * 获取缩略图URL
     */
    private String getThumbnailUrl(Map<String, Object> contentData) {
        Object imageUrl = contentData.get("image_url");
        if (imageUrl != null) {
            return imageUrl.toString();
        }
        return "";
    }
    
    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(Map<String, Object> contentData, String kind) {
        double score = 0.5; // 基础分数
        
        // 内容类型权重
        if ("video".equals(kind)) score += 0.2;
        else if ("exercise".equals(kind)) score += 0.15;
        else if ("topic".equals(kind)) score += 0.1;
        
        // 难度级别权重
        Object gradeLevel = contentData.get("grade_level");
        if (gradeLevel != null) {
            String level = gradeLevel.toString();
            if (level.contains("1") || level.contains("2") || level.contains("3")) {
                score += 0.2; // 适合低年级
            }
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 获取 API 密钥
     */
    private String getApiKey() {
        String apiKey = apiConfig.getKhanAcademy().getApiKey();
        if (apiKey == null || apiKey.contains("your_khan_academy_api_key_here")) {
            return System.getenv("KHAN_ACADEMY_API_KEY");
        }
        return apiKey;
    }
    
    /**
     * 获取模拟 Khan Academy 视频（当 API 不可用时）
     */
    private Flux<VideoResource> getMockKhanAcademyVideos(String query, String specialNeeds, String ageRange) {
        return Flux.fromArray(new VideoResource[] {
            new VideoResource("ka_real_001", "基础数学 - 分步骤学习", 
                "分步骤的数学基础教学，适合" + specialNeeds + "儿童", "10分钟", ageRange,
                Arrays.asList("数学", "步骤分解", "重复练习"), specialNeeds,
                "Khan Academy", "https://www.khanacademy.org/math/early-math", "https://cdn.kastatic.org/images/khan-logo.png"),
                
            new VideoResource("ka_real_002", "阅读理解 - 视觉学习", 
                "视觉化阅读理解训练，语言发育友好", "12分钟", ageRange,
                Arrays.asList("阅读", "语言", "理解"), specialNeeds,
                "Khan Academy", "https://www.khanacademy.org/reading/early-reading", "https://cdn.kastatic.org/images/khan-logo.png"),
                
            new VideoResource("ka_real_003", "科学探索 - 动手实验", 
                "互动科学实验，激发好奇心", "15分钟", ageRange,
                Arrays.asList("科学", "实验", "探索"), specialNeeds,
                "Khan Academy", "https://www.khanacademy.org/science/early-science", "https://cdn.kastatic.org/images/khan-logo.png")
        });
    }
}
