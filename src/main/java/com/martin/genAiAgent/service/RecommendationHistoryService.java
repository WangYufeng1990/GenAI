package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.RecommendationHistory;
import com.martin.genAiAgent.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RecommendationHistoryService {
    
    private final RecommendationHistoryRepository recommendationHistoryRepository;
    
    /**
     * 保存推荐历史
     */
    public void saveRecommendation(String userId, RecommendationHistory history) {
        history.setUserId(userId);
        recommendationHistoryRepository.save(history);
        log.info("保存推荐历史: userId={}, videoId={}", userId, history.getVideoId());
    }
    
    /**
     * 批量保存推荐历史
     */
    public void saveRecommendations(String userId, List<RecommendationHistory> histories) {
        histories.forEach(history -> history.setUserId(userId));
        recommendationHistoryRepository.saveAll(histories);
        log.info("批量保存推荐历史: userId={}, count={}", userId, histories.size());
    }
    
    /**
     * 记录用户评分
     */
    public void recordUserRating(String userId, String videoId, int rating) {
        List<RecommendationHistory> histories = recommendationHistoryRepository.findByVideoIdOrderByCreatedAtDesc(videoId);
        
        // 找到该用户的推荐记录
        RecommendationHistory userHistory = histories.stream()
            .filter(h -> h.getUserId().equals(userId))
            .findFirst()
            .orElse(null);
        
        if (userHistory != null) {
            userHistory.setUserRating(rating);
            recommendationHistoryRepository.save(userHistory);
            log.info("记录用户评分: userId={}, videoId={}, rating={}", userId, videoId, rating);
        } else {
            log.warn("未找到用户的推荐记录: userId={}, videoId={}", userId, videoId);
        }
    }
    
    /**
     * 获取用户推荐历史
     */
    public List<RecommendationHistory> getUserRecommendationHistory(String userId) {
        return recommendationHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * 获取用户已评分的推荐
     */
    public List<RecommendationHistory> getUserRatedRecommendations(String userId) {
        return recommendationHistoryRepository.findByUserIdAndUserRatingIsNotNullOrderByCreatedAtDesc(userId);
    }
    
    /**
     * 获取用户平均评分
     */
    public Double getUserAverageRating(String userId) {
        return recommendationHistoryRepository.getAverageRatingByUserId(userId);
    }
    
    /**
     * 获取最近的推荐历史
     */
    public List<RecommendationHistory> getRecentRecommendations(String userId, int limit) {
        return recommendationHistoryRepository.findRecentByUserId(userId, limit);
    }
    
    /**
     * 获取特定时间范围内的推荐历史
     */
    public List<RecommendationHistory> getRecommendationsInTimeRange(
        String userId, LocalDateTime startTime, LocalDateTime endTime) {
        return recommendationHistoryRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, startTime, endTime);
    }
}
