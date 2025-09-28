package xyz.Brownie.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserCountService {
    private static final Logger log = LoggerFactory.getLogger(UserCountService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FOLLOWS_COUNT_KEY = "user:follows:count";
    private static final String FANS_COUNT_KEY = "user:fans:count";

    // 基本计数操作
    public void incrementFollowsCount(Long userId) {
        stringRedisTemplate.opsForHash().increment(FOLLOWS_COUNT_KEY, userId.toString(), 1);
    }

    public void incrementFansCount(Long userId) {
        stringRedisTemplate.opsForHash().increment(FANS_COUNT_KEY, userId.toString(), 1);
    }

    public void decrementFollowsCount(Long userId) {
        stringRedisTemplate.opsForHash().increment(FOLLOWS_COUNT_KEY, userId.toString(), -1);
    }

    public void decrementFansCount(Long userId) {
        stringRedisTemplate.opsForHash().increment(FANS_COUNT_KEY, userId.toString(), -1);
    }

    public Integer getFollowsCount(Long userId) {
        String count = (String) stringRedisTemplate.opsForHash().get(FOLLOWS_COUNT_KEY, userId.toString());
        return count != null ? Integer.parseInt(count) : null;
    }

    public Integer getFansCount(Long userId) {
        String count = (String) stringRedisTemplate.opsForHash().get(FANS_COUNT_KEY, userId.toString());
        return count != null ? Integer.parseInt(count) : null;
    }

    // 获取计数映射
    public Map<String, Integer> getFollowsCountMap() {
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(FOLLOWS_COUNT_KEY);
            return convertToIntegerMap(entries);
        } catch (Exception e) {
            log.error("获取关注数映射失败", e);
            return new HashMap<>();
        }
    }

    public Map<String, Integer> getFansCountMap() {
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(FANS_COUNT_KEY);
            return convertToIntegerMap(entries);
        } catch (Exception e) {
            log.error("获取粉丝数映射失败", e);
            return new HashMap<>();
        }
    }

    // 重置计数
    public void resetFollowsCount(Long userId, int delta) {
        stringRedisTemplate.opsForHash().increment(FOLLOWS_COUNT_KEY, userId.toString(), -delta);
    }

    public void resetFansCount(Long userId, int delta) {
        stringRedisTemplate.opsForHash().increment(FANS_COUNT_KEY, userId.toString(), -delta);
    }

    // 私有工具方法
    private Map<String, Integer> convertToIntegerMap(Map<Object, Object> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return new HashMap<>();
        }

        return rawMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> {
                            try {
                                return Integer.parseInt(entry.getValue().toString());
                            } catch (NumberFormatException e) {
                                log.warn("无法将value转换为整数: {}", entry.getValue());
                                return 0;
                            }
                        }
                ));
    }
}