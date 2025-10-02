package xyz.Brownie.service;

import xyz.Brownie.bean.dto.UserFollowsDto;
import xyz.Brownie.bean.entity.UserFollows;
import com.baomidou.mybatisplus.extension.service.IService;
import xyz.Brownie.utils.Result;

/**
* @author Welt
* @description 针对表【user_follows】的数据库操作Service
* @createDate 2025-09-27 15:01:59
*/
public interface UserFollowsService extends IService<UserFollows> {

    Result getFollowsListByUserId(Long id);

    Result addFollows(UserFollowsDto userFollows);

    Result removeFollows(UserFollowsDto userFollows);
}
