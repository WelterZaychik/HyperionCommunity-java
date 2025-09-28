package xyz.Brownie.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import xyz.Brownie.bean.entity.User;
import xyz.Brownie.utils.Result;

@Component
@FeignClient("module-user")
@RequestMapping("/user")
public interface UserClient {

    /**
     * 调整关注数（可正可负）
     */
    @PutMapping("/{userId}/adjust-follows")
    Result adjustFollowsCount(@PathVariable("userId") Long userId, @RequestParam("delta") int delta);

    /**
     * 调整粉丝数（可正可负）
     */
    @PutMapping("/{userId}/adjust-fans")
    Result adjustFansCount(@PathVariable("userId") Long userId, @RequestParam("delta") int delta);
}
