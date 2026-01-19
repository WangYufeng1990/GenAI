package com.martin.genAiAgent.repository;

import com.martin.genAiAgent.model.RecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {
    
    // 根据用户ID查找推荐历史
    List<RecommendationHistory> findByUserIdOrderByCreatedAtDesc(String userId);
    
    // 根据用户ID和评分查找
    List<RecommendationHistory> findByUserIdAndUserRatingIsNotNullOrderByCreatedAtDesc(String userId);
    
    // 查找用户最近的推荐（限制数量）
    List<RecommendationHistory> findTop10ByUserIdOrderByCreatedAtDesc(String userId);
    
    // 统计用户的平均评分
    @Query("SELECT AVG(r.userRating) FROM RecommendationHistory r WHERE r.userId = :userId AND r.userRating IS NOT NULL")
    Double getAverageRatingByUserId(@Param("userId") String userId);
    
    // 查找特定时间范围内的推荐
    List<RecommendationHistory> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        String userId, LocalDateTime startTime, LocalDateTime endTime);
    
    // 根据视频ID查找推荐历史
    List<RecommendationHistory> findByVideoIdOrderByCreatedAtDesc(String videoId);
}
