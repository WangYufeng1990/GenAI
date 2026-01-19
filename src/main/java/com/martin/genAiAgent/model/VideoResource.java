package com.martin.genAiAgent.model;

import java.util.List;
import java.util.Arrays;

/**
 * 视频资源模型
 */
public class VideoResource {
    public String id;
    public String title;
    public String description;
    public String duration;
    public String ageRange;
    public List<String> tags;
    public String specialNeedsFocus;
    public double relevanceScore;
    public String source; // YouTube, Khan Academy, PBS Kids 等
    public String videoUrl; // 实际视频链接
    public String thumbnailUrl; // 缩略图链接
    
    public VideoResource() {}
    
    public VideoResource(String id, String title, String description, String duration, 
                     String ageRange, List<String> tags, String specialNeedsFocus) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.ageRange = ageRange;
        this.tags = tags;
        this.specialNeedsFocus = specialNeedsFocus;
        this.relevanceScore = 0.0;
    }
    
    public VideoResource(String id, String title, String description, String duration, 
                     String ageRange, List<String> tags, String specialNeedsFocus, 
                     String source, String videoUrl, String thumbnailUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.ageRange = ageRange;
        this.tags = tags;
        this.specialNeedsFocus = specialNeedsFocus;
        this.relevanceScore = 0.0;
        this.source = source;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
    
    // Getter 和 Setter 方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    
    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public String getSpecialNeedsFocus() { return specialNeedsFocus; }
    public void setSpecialNeedsFocus(String specialNeedsFocus) { this.specialNeedsFocus = specialNeedsFocus; }
    
    public double getRelevanceScore() { return relevanceScore; }
    public void setRelevanceScore(double relevanceScore) { this.relevanceScore = relevanceScore; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    @Override
    public String toString() {
        return String.format("VideoResource{id='%s', title='%s', source='%s', score=%.2f}", 
                          id, title, source, relevanceScore);
    }
}
