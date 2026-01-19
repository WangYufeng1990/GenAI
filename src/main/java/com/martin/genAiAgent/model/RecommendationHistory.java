package com.martin.genAiAgent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "video_id", nullable = false)
    private String videoId;
    
    @Column(name = "video_title", nullable = false)
    private String videoTitle;
    
    @Column(name = "video_url")
    private String videoUrl;
    
    @Column(name = "source_platform")
    private String sourcePlatform;
    
    @Column(name = "relevance_score")
    private Double relevanceScore;
    
    @Column(name = "user_rating")
    private Integer userRating;
    
    @Column(name = "special_needs")
    private String specialNeeds;
    
    @Column(name = "child_age")
    private String childAge;
    
    @Column(name = "learning_goal")
    private String learningGoal;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
