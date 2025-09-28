package xyz.Brownie.task;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import xyz.Brownie.client.UserClient;
import xyz.Brownie.service.impl.UserCountService;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserCountSyncTask {
    private static final Logger log = LoggerFactory.getLogger(UserCountSyncTask.class);

    @Autowired
    private UserClient userClient;

    @Autowired
    private  UserCountService userCountService;

    /**
     * 每5分钟同步一次关注数
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncFollowsCount() {
        Map<String, Integer> followsMap = userCountService.getFollowsCountMap();
        batchSyncCounts(followsMap, "follows");
    }

    /**
     * 每5分钟同步一次粉丝数
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncFansCount() {
        Map<String, Integer> fansMap = userCountService.getFansCountMap();
        batchSyncCounts(fansMap, "fans");
    }

    /**
     * 批量同步计数
     */
    private void batchSyncCounts(Map<String, Integer> countMap, String countType) {
        if (countMap == null || countMap.isEmpty()) {
            return;
        }

        countMap.forEach((userIdStr, countObj) -> {
            try {
                Long userId = Long.parseLong(userIdStr.toString());
                int delta = ((Number) countObj).intValue();

                if (delta != 0) {
                    if ("follows".equals(countType)) {
                        userClient.adjustFollowsCount(userId, delta);
                    } else {
                        userClient.adjustFansCount(userId, delta);
                    }

                    // 同步成功后，重置Redis中的增量
                    if ("follows".equals(countType)) {
                        userCountService.resetFollowsCount(userId, delta);
                    } else {
                        userCountService.resetFansCount(userId, delta);
                    }
                }
            } catch (Exception e) {
                log.error("同步{}计数失败，userId: {}", countType, userIdStr, e);
            }
        });
    }
}