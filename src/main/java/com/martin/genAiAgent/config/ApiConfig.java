package com.martin.genAiAgent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

/**
 * 外部 API 配置类
 */
@Configuration
@ConfigurationProperties(prefix = "api")
public class ApiConfig {
    
    private YouTube youtube = new YouTube();
    private KhanAcademy khanAcademy = new KhanAcademy();
    private PBSKids pbsKids = new PBSKids();
    private SesameStreet sesameStreet = new SesameStreet();
    private NationalGeographicKids nationalGeographicKids = new NationalGeographicKids();
    private RateLimit rateLimit = new RateLimit();
    
    // Getters and Setters
    public YouTube getYoutube() { return youtube; }
    public void setYoutube(YouTube youtube) { this.youtube = youtube; }
    
    public KhanAcademy getKhanAcademy() { return khanAcademy; }
    public void setKhanAcademy(KhanAcademy khanAcademy) { this.khanAcademy = khanAcademy; }
    
    public PBSKids getPbsKids() { return pbsKids; }
    public void setPbsKids(PBSKids pbsKids) { this.pbsKids = pbsKids; }
    
    public SesameStreet getSesameStreet() { return sesameStreet; }
    public void setSesameStreet(SesameStreet sesameStreet) { this.sesameStreet = sesameStreet; }
    
    public NationalGeographicKids getNationalGeographicKids() { return nationalGeographicKids; }
    public void setNationalGeographicKids(NationalGeographicKids nationalGeographicKids) { this.nationalGeographicKids = nationalGeographicKids; }
    
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    
    // YouTube 配置
    public static class YouTube {
        private String apiKey;
        private String baseUrl = "https://www.googleapis.com/youtube/v3";
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
    
    // Khan Academy 配置
    public static class KhanAcademy {
        private String apiKey;
        private String baseUrl = "https://www.khanacademy.org/api/v1";
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
    
    // PBS Kids 配置
    public static class PBSKids {
        private String apiKey;
        private String baseUrl = "https://pbskids.org/api";
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
    
    // Sesame Street 配置
    public static class SesameStreet {
        private String apiKey;
        private String baseUrl = "https://api.sesameworkshop.org";
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
    
    // National Geographic Kids 配置
    public static class NationalGeographicKids {
        private String apiKey;
        private String baseUrl = "https://api.nationalgeographic.com";
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
    
    // 限流配置
    public static class RateLimit {
        private int requestsPerMinute = 60;
        private int requestsPerHour = 1000;
        
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        
        public int getRequestsPerHour() { return requestsPerHour; }
        public void setRequestsPerHour(int requestsPerHour) { this.requestsPerHour = requestsPerHour; }
    }
    
    // WebClient Bean 配置
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
    
    @Bean
    public WebClient youtubeWebClient() {
        return WebClient.builder()
                .baseUrl(youtube.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    @Bean
    public WebClient khanAcademyWebClient() {
        return WebClient.builder()
                .baseUrl(khanAcademy.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    @Bean
    public WebClient pbsKidsWebClient() {
        return WebClient.builder()
                .baseUrl(pbsKids.getBaseUrl())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    // ChatClient Bean 配置
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
    
    // CacheManager Bean 配置
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("videoCache", "searchCache", "userCache");
    }
}
