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
import java.util.stream.Collectors;

/**
 * YouTube Data API 服务
 */
@Service
public class YouTubeApiService {
    
    private final WebClient webClient;
    private final ApiConfig apiConfig;
    
    @Autowired
    public YouTubeApiService(WebClient youtubeWebClient, ApiConfig apiConfig) {
        this.webClient = youtubeWebClient;
        this.apiConfig = apiConfig;
    }
    
    /**
     * 搜索教育视频
     */
    @Cacheable(value = "youtube-cache", key = "#query + '-' + #specialNeeds + '-' + #ageRange")
    public Flux<VideoResource> searchEducationalVideos(String query, String specialNeeds, String ageRange, int maxResults) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return Flux.error(new RuntimeException("YouTube API key not configured"));
        }
        
        // 构建搜索查询
        String searchQuery = buildYouTubeSearchQuery(query, specialNeeds, ageRange);
        
        // YouTube Data API 搜索
        return searchYouTubeVideos(searchQuery, apiKey, maxResults)
                .flatMap(this::getVideoDetails)
                .filter(Objects::nonNull)
                .map(this::convertToVideoResource)
                .filter(Objects::nonNull)
                .doOnError(error -> System.err.println("YouTube API error: " + error.getMessage()))
                .onErrorResume(error -> {
                    System.err.println("YouTube API fallback to mock data: " + error.getMessage());
                    return getMockYouTubeVideos(query, specialNeeds, ageRange);
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .jitter(0.5));
    }
    
    /**
     * 搜索 YouTube 视频
     */
    private Flux<Map<String, Object>> searchYouTubeVideos(String query, String apiKey, int maxResults) {
        String url = String.format(
            "/search?part=snippet&type=video&q=%s&maxResults=%d&videoDuration=short&relevanceLanguage=zh&key=%s",
            query, maxResults, apiKey
        );
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    return items != null ? Flux.fromIterable(items) : Flux.empty();
                })
                .filter(item -> item.containsKey("id"))
                .filter(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> id = (Map<String, Object>) item.get("id");
                    return id.containsKey("videoId");
                });
    }
    
    /**
     * 获取视频详细信息
     */
    private Mono<Map<String, Object>> getVideoDetails(Map<String, Object> searchResult) {
        @SuppressWarnings("unchecked")
        Map<String, Object> id = (Map<String, Object>) searchResult.get("id");
        String videoId = (String) id.get("videoId");
        
        String apiKey = getApiKey();
        String url = String.format(
            "/videos?part=contentDetails,statistics&id=%s&key=%s",
            videoId, apiKey
        );
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    if (items != null && !items.isEmpty()) {
                        Map<String, Object> videoDetails = items.get(0);
                        // 合并搜索结果和详细信息
                        Map<String, Object> combined = new HashMap<>(searchResult);
                        combined.putAll(videoDetails);
                        return combined;
                    }
                    return null;
                });
    }
    
    /**
     * 转换为 VideoResource 对象
     */
    @SuppressWarnings("unchecked")
    private VideoResource convertToVideoResource(Map<String, Object> videoData) {
        try {
            Map<String, Object> snippet = (Map<String, Object>) videoData.get("snippet");
            Map<String, Object> contentDetails = (Map<String, Object>) videoData.get("contentDetails");
            Map<String, Object> statistics = (Map<String, Object>) videoData.get("statistics");
            Map<String, Object> id = (Map<String, Object>) videoData.get("id");
            
            String videoId = (String) id.get("videoId");
            String title = (String) snippet.get("title");
            String description = (String) snippet.get("description");
            String channelTitle = (String) snippet.get("channelTitle");
            String publishedAt = (String) snippet.get("publishedAt");
            
            // 解析时长
            String duration = parseDuration((String) contentDetails.get("duration"));
            
            // 解析统计数据
            String viewCount = statistics != null ? (String) statistics.get("viewCount") : "0";
            String likeCount = statistics != null ? (String) statistics.get("likeCount") : "0";
            
            // 分析适合年龄和特殊需求
            String ageRange = analyzeAgeRange(title, description, channelTitle);
            List<String> tags = extractTags(title, description);
            String specialNeedsFocus = analyzeSpecialNeeds(title, description, tags);
            
            VideoResource video = new VideoResource();
            video.setId("yt_" + videoId);
            video.setTitle(title);
            video.setDescription(description);
            video.setDuration(duration);
            video.setAgeRange(ageRange);
            video.setTags(tags);
            video.setSpecialNeedsFocus(specialNeedsFocus);
            video.setSource("YouTube");
            video.setVideoUrl("https://www.youtube.com/watch?v=" + videoId);
            video.setThumbnailUrl(getThumbnailUrl(snippet));
            
            // 计算相关性分数
            double relevanceScore = calculateRelevanceScore(video, viewCount, likeCount, publishedAt);
            video.setRelevanceScore(relevanceScore);
            
            return video;
            
        } catch (Exception e) {
            System.err.println("Error converting YouTube video: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建 YouTube 搜索查询
     */
    private String buildYouTubeSearchQuery(String query, String specialNeeds, String ageRange) {
        StringBuilder sb = new StringBuilder();
        
        // 基础查询
        sb.append(query);
        
        // 添加教育相关关键词
        sb.append(" educational");
        
        // 根据特殊需求添加关键词
        switch (specialNeeds.toLowerCase()) {
            case "自闭症":
                sb.append(" autism friendly visual learning");
                break;
            case "多动症":
                sb.append(" ADHD interactive engaging");
                break;
            case "学习障碍":
                sb.append(" learning disability step by step");
                break;
            case "感觉统合障碍":
                sb.append(" sensory integration multisensory");
                break;
            case "社交障碍":
                sb.append(" social skills therapy");
                break;
            case "语言发育迟缓":
                sb.append(" speech therapy language development");
                break;
        }
        
        // 根据年龄添加关键词
        if (ageRange.contains("3") || ageRange.contains("4")) {
            sb.append(" preschool toddler");
        } else if (ageRange.contains("5") || ageRange.contains("6")) {
            sb.append(" kindergarten");
        } else if (ageRange.contains("7") || ageRange.contains("8")) {
            sb.append(" elementary grade 1-2");
        } else if (ageRange.contains("9") || ageRange.contains("10")) {
            sb.append(" elementary grade 3-4");
        }
        
        // 添加视频类型限制
        sb.append(" kids children animation");
        
        return sb.toString().replace(" ", "+");
    }
    
    /**
     * 解析视频时长
     */
    private String parseDuration(String isoDuration) {
        if (isoDuration == null) return "未知";
        
        try {
            // YouTube 使用 ISO 8601 格式 PT4M13S
            if (isoDuration.startsWith("PT")) {
                String duration = isoDuration.substring(2);
                
                if (duration.contains("H")) {
                    return duration.replace("H", "小时").replace("M", "分钟").replace("S", "秒");
                } else if (duration.contains("M")) {
                    return duration.replace("M", "分钟").replace("S", "秒");
                } else {
                    return duration.replace("S", "秒");
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing duration: " + e.getMessage());
        }
        
        return "未知";
    }
    
    /**
     * 分析适合年龄
     */
    private String analyzeAgeRange(String title, String description, String channelTitle) {
        String content = (title + " " + description + " " + channelTitle).toLowerCase();
        
        if (content.contains("toddler") || content.contains("2-3") || content.contains("3岁")) {
            return "2-4岁";
        } else if (content.contains("preschool") || content.contains("4-5") || content.contains("4岁") || content.contains("5岁")) {
            return "4-6岁";
        } else if (content.contains("kindergarten") || content.contains("6-7") || content.contains("6岁") || content.contains("7岁")) {
            return "6-8岁";
        } else if (content.contains("elementary") || content.contains("8-10") || content.contains("8岁") || content.contains("9岁") || content.contains("10岁")) {
            return "8-10岁";
        }
        
        return "3-8岁"; // 默认范围
    }
    
    /**
     * 提取标签
     */
    private List<String> extractTags(String title, String description) {
        List<String> tags = new ArrayList<>();
        
        String content = (title + " " + description).toLowerCase();
        
        // 教育内容标签
        if (content.contains("abc") || content.contains("alphabet")) tags.add("字母学习");
        if (content.contains("number") || content.contains("count")) tags.add("数字学习");
        if (content.contains("color") || content.contains("colour")) tags.add("颜色学习");
        if (content.contains("shape")) tags.add("形状学习");
        if (content.contains("social") || content.contains("emotion")) tags.add("社交技能");
        if (content.contains("science")) tags.add("科学探索");
        if (content.contains("math")) tags.add("数学");
        if (content.contains("reading") || content.contains("story")) tags.add("阅读");
        if (content.contains("music") || content.contains("song")) tags.add("音乐");
        if (content.contains("art") || content.contains("draw")) tags.add("美术");
        
        // 学习方式标签
        if (content.contains("interactive")) tags.add("互动");
        if (content.contains("animation") || content.contains("cartoon")) tags.add("动画");
        if (content.contains("visual")) tags.add("视觉学习");
        if (content.contains("game")) tags.add("游戏");
        if (content.contains("puzzle")) tags.add("拼图");
        
        return tags;
    }
    
    /**
     * 分析特殊需求焦点
     */
    private String analyzeSpecialNeeds(String title, String description, List<String> tags) {
        String content = (title + " " + description).toLowerCase();
        
        if (content.contains("autism") || content.contains("special needs") || 
            tags.contains("视觉学习") || content.contains("visual")) {
            return "自闭症";
        } else if (content.contains("adhd") || content.contains("attention") || 
                  tags.contains("互动") || content.contains("interactive")) {
            return "多动症";
        } else if (content.contains("learning disability") || content.contains("step by step") ||
                  tags.contains("步骤分解")) {
            return "学习障碍";
        } else if (content.contains("sensory") || content.contains("multisensory") ||
                  tags.contains("多感官")) {
            return "感觉统合障碍";
        } else if (content.contains("social") || content.contains("emotion") ||
                  tags.contains("社交技能")) {
            return "社交障碍";
        } else if (content.contains("speech") || content.contains("language") ||
                  tags.contains("语言")) {
            return "语言发育迟缓";
        }
        
        return "通用"; // 默认
    }
    
    /**
     * 获取缩略图URL
     */
    @SuppressWarnings("unchecked")
    private String getThumbnailUrl(Map<String, Object> snippet) {
        Map<String, Object> thumbnails = (Map<String, Object>) snippet.get("thumbnails");
        if (thumbnails != null) {
            Map<String, Object> highQuality = (Map<String, Object>) thumbnails.get("high");
            if (highQuality != null) {
                return (String) highQuality.get("url");
            }
            Map<String, Object> mediumQuality = (Map<String, Object>) thumbnails.get("medium");
            if (mediumQuality != null) {
                return (String) mediumQuality.get("url");
            }
        }
        return "";
    }
    
    /**
     * 计算相关性分数
     */
    private double calculateRelevanceScore(VideoResource video, String viewCount, String likeCount, String publishedAt) {
        double score = 0.5; // 基础分数
        
        try {
            // 观看次数权重
            long views = Long.parseLong(viewCount);
            if (views > 1000000) score += 0.2;
            else if (views > 100000) score += 0.15;
            else if (views > 10000) score += 0.1;
            
            // 点赞数权重
            long likes = Long.parseLong(likeCount);
            if (likes > 10000) score += 0.2;
            else if (likes > 1000) score += 0.15;
            else if (likes > 100) score += 0.1;
            
            // 发布时间权重（越新越好）
            if (publishedAt != null && publishedAt.contains("2024")) score += 0.1;
            else if (publishedAt != null && publishedAt.contains("2023")) score += 0.05;
            
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * 获取 API 密钥
     */
    private String getApiKey() {
        String apiKey = apiConfig.getYoutube().getApiKey();
        if (apiKey == null || apiKey.contains("your_youtube_api_key_here")) {
            return System.getenv("YOUTUBE_API_KEY");
        }
        return apiKey;
    }
    
    /**
     * 获取模拟 YouTube 视频（当 API 不可用时）
     */
    private Flux<VideoResource> getMockYouTubeVideos(String query, String specialNeeds, String ageRange) {
        return Flux.fromArray(new VideoResource[] {
            new VideoResource("yt_mock_001", "ABC Song for Kids - " + specialNeeds + " Friendly", 
                "适合" + specialNeeds + "儿童的ABC学习歌曲，缓慢节奏，重复练习", "5分钟", ageRange,
                Arrays.asList("字母学习", "音乐", "重复练习"), specialNeeds,
                "YouTube", "https://youtube.com/watch?v=mock1", "https://img.youtube.com/vi/mock1/hqdefault.jpg"),
                
            new VideoResource("yt_mock_002", "Counting 1-10 - Interactive Learning", 
                "互动数字学习，专为" + specialNeeds + "儿童设计", "8分钟", ageRange,
                Arrays.asList("数字", "互动", "游戏"), specialNeeds,
                "YouTube", "https://youtube.com/watch?v=mock2", "https://img.youtube.com/vi/mock2/hqdefault.jpg")
        });
    }
}
