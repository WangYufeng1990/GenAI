package com.martin.genAiAgent.service;

import com.martin.genAiAgent.model.User;
import com.martin.genAiAgent.model.UserProfile;
import com.martin.genAiAgent.repository.UserRepository;
import com.martin.genAiAgent.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserProfileService {
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    
    /**
     * 获取用户画像
     */
    public Optional<UserProfile> getUserProfile(String userId) {
        return userProfileRepository.findById(userId);
    }
    
    /**
     * 创建或更新用户画像
     */
    public UserProfile saveOrUpdateUserProfile(String userId, String childAge, 
                                         String specialNeeds, String learningGoals, 
                                         List<String> preferences) {
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseGet(() -> {
                UserProfile newProfile = new UserProfile();
                newProfile.setUserId(userId);
                return newProfile;
            });
        
        profile.setChildAge(childAge);
        profile.setSpecialNeeds(specialNeeds);
        profile.setLearningGoals(learningGoals);
        profile.setPreferences(preferences);
        
        UserProfile savedProfile = userProfileRepository.save(profile);
        log.info("用户画像已保存: userId={}, childAge={}, specialNeeds={}", 
                 userId, childAge, specialNeeds);
        
        return savedProfile;
    }
    
    /**
     * 更新用户偏好
     */
    public void updateUserPreferences(String userId, List<String> newPreferences) {
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户画像不存在: " + userId));
        
        // 合并新旧偏好
        List<String> currentPreferences = profile.getPreferences();
        if (currentPreferences != null) {
            newPreferences.forEach(pref -> {
                if (!currentPreferences.contains(pref)) {
                    currentPreferences.add(pref);
                }
            });
            profile.setPreferences(currentPreferences);
        } else {
            profile.setPreferences(newPreferences);
        }
        
        userProfileRepository.save(profile);
        log.info("用户偏好已更新: userId={}, newPreferences={}", userId, newPreferences);
    }
    
    /**
     * 获取用户信息
     */
    public Optional<User> getUser(String userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * 根据用户名获取用户
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * 检查用户是否存在
     */
    public boolean userExists(String userId) {
        return userRepository.existsById(userId);
    }
    
    /**
     * 删除用户画像
     */
    public void deleteUserProfile(String userId) {
        userProfileRepository.deleteById(userId);
        log.info("用户画像已删除: userId={}", userId);
    }
}
