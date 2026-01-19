package com.martin.genAiAgent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    
    @Id
    private String userId; // 对应 users 表的 id
    
    @Column(name = "child_age")
    private String childAge;
    
    @Column(name = "special_needs", columnDefinition = "TEXT")
    private String specialNeeds;
    
    @Column(name = "learning_goals", columnDefinition = "TEXT")
    private String learningGoals;
    
    @ElementCollection
    @CollectionTable(name = "user_preferences", joinColumns = @JoinColumn(name = "user_id"))
    private List<String> preferences;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
