package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.RecommendationHistory;
import com.martin.genAiAgent.model.VideoResource;
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
    public RecommendationHistory saveRecommendation(String userId, VideoResource video, 
                                            String specialNeeds, String childAge, 
                                            String learningGoal) {
        RecommendationHistory history = new RecommendationHistory();
        history.setUserId(userId);
        history.setVideoId(video.getId());
        history.setVideoTitle(video.getTitle());
        history.setVideoUrl(video.getVideoUrl());
        history.setSourcePlatform(video.getSource());
        history.setRelevanceScore(video.getRelevanceScore());
        history.setSpecialNeeds(specialNeeds);
        history.setChildAge(childAge);
        history.setLearningGoal(learningGoal);
        
        RecommendationHistory savedHistory = recommendationHistoryRepository.save(history);
        log.info("推荐历史已保存: userId={}, videoId={}, score={}", 
                 userId, video.getId(), video.getRelevanceScore());
        
        return savedHistory;
    }
    
    /**
     * 批量保存推荐历史
     */
    public void saveRecommendations(String userId, List<VideoResource> videos, 
                               String specialNeeds, String childAge, 
                               String learningGoal) {
        List<RecommendationHistory> histories = videos.stream()
            .map(video -> {
                RecommendationHistory history = new RecommendationHistory();
                history.setUserId(userId);
                history.setVideoId(video.getId());
                history.setVideoTitle(video.getTitle());
                history.setVideoUrl(video.getVideoUrl());
                history.setSourcePlatform(video.getSource());
                history.setRelevanceScore(video.getRelevanceScore());
                history.setSpecialNeeds(specialNeeds);
                history.setChildAge(childAge);
                history.setLearningGoal(learningGoal);
                return history;
            })
            .toList();
        
        recommendationHistoryRepository.saveAll(histories);
        log.info("批量保存推荐历史: userId={}, count={}", userId, videos.size());
    }
    
    /**
     * 记录用户评分
     */
    public void recordUserRating(String userId, String videoId, int rating) {
        List<RecommendationHistory> histories = recommendationHistoryRepository
            .findByVideoIdOrderByCreatedAtDesc(videoId);
        
        // 找到该用户的推荐记录
        RecommendationHistory userHistory = histories.stream()
            .filter(h -> h.getUserId().equals(userId))
            .findFirst()
            .orElse(null);
        
        if (userHistory != null) {
            userHistory.setUserRating(rating);
            recommendationHistoryRepository.save(userHistory);
            log.info("用户评分已记录: userId={}, videoId={}, rating={}", userId, videoId, rating);
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
     * 获取用户已评分的推荐历史
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
        return recommendationHistoryRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId, startTime, endTime);
    }
}
