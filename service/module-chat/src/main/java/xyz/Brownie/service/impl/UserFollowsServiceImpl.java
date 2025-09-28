package xyz.Brownie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import xyz.Brownie.bean.dto.UserFollowsDto;
import xyz.Brownie.bean.entity.User;
import xyz.Brownie.bean.entity.UserFollows;
import xyz.Brownie.bean.vo.FollowsVo;
import xyz.Brownie.client.UserClient;
import xyz.Brownie.utils.BeanCopyUtils;
import xyz.Brownie.constants.ResponseCode;
import xyz.Brownie.utils.Result;
import xyz.Brownie.service.UserFollowsService;
import xyz.Brownie.mapper.UserFollowsMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author Welt
* @description 针对表【user_follows】的数据库操作Service实现
* @createDate 2025-09-27 15:01:59
*/
@Service
public class UserFollowsServiceImpl extends ServiceImpl<UserFollowsMapper, UserFollows>
    implements UserFollowsService{

    private static final Logger log = LoggerFactory.getLogger(UserFollowsServiceImpl.class);

    @Autowired
    private UserFollowsMapper userFollowsMapper;

    @Autowired
    private UserCountService userCountService;

    private Map res;

    @Override
    public Result getFollowsListByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            log.warn("Invalid userId parameter: {}", userId);
            res.put("msg","参数传递错误");
            return Result.fail(ResponseCode.CodeDefault, res);
        }

        res = new HashMap();
        List<User> friendships = userFollowsMapper.getFollowsListByUserId(userId);
        List<FollowsVo> followsVos = BeanCopyUtils.copyBeanList(friendships, FollowsVo.class);
        res.put("friendships", followsVos);
        return Result.suc(ResponseCode.Code200,res);
    }

    @Override
    public Result addFollows(UserFollowsDto userFollowsDto) {
        res = new HashMap();
        // 1. 参数校验
        if (userFollowsDto.getUserId() == null || userFollowsDto.getFollowsId() == null) {
            res.put("msg","参数不能为空");
            return Result.fail(ResponseCode.CodeDefault, res);
        }

        // 2. 不能关注自己
        if (userFollowsDto.getUserId().equals(userFollowsDto.getFollowsId())) {
            res.put("msg","不能关注自己");
            return Result.fail(ResponseCode.CodeDefault, res);
        }

        // 3. 检查是否已存在关注关系
        LambdaQueryWrapper<UserFollows> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollows::getUserId, userFollowsDto.getUserId())
                .eq(UserFollows::getFollowsId, userFollowsDto.getFollowsId());
        UserFollows userFollows = userFollowsMapper.selectOne(wrapper);

        // 4. 如果记录已存在
        if (userFollows != null) {
            if (userFollows.getIsDelete() == 1) {
                // 如果是已删除状态，恢复关注
                userFollows.setIsDelete(0);
                userFollows.setStatus(0); // 重置为单方关注状态
                userFollowsMapper.updateById(userFollows);
            } else {
                res.put("msg","已关注该用户");
                return Result.fail(ResponseCode.CodeDefault, res);
            }
        } else {
            // 5. 创建新的关注记录
            userFollows = new UserFollows();
            userFollows.setUserId(userFollowsDto.getUserId());
            userFollows.setFollowsId(userFollowsDto.getFollowsId());
            userFollows.setStatus(0); // 初始状态为单方关注
            userFollowsMapper.insert(userFollows);
        }

        // 6. 检查是否形成互相关注
        LambdaQueryWrapper<UserFollows> reverseWrapper = new LambdaQueryWrapper<>();
        reverseWrapper.eq(UserFollows::getUserId, userFollowsDto.getFollowsId())
                .eq(UserFollows::getFollowsId, userFollowsDto.getUserId())
                .eq(UserFollows::getIsDelete, 0);
        UserFollows reverseFollow = userFollowsMapper.selectOne(reverseWrapper);

        if (reverseFollow != null) {
            // 7. 如果对方也关注了我，更新为互相关注状态
            userFollows.setStatus(1);
            userFollowsMapper.updateById(userFollows);

            reverseFollow.setStatus(1);
            userFollowsMapper.updateById(reverseFollow);
        }

        // 8. 更新用户表中的关注数和粉丝数
        userCountService.incrementFollowsCount(userFollowsDto.getUserId());
        userCountService.incrementFansCount(userFollowsDto.getFollowsId());

        return Result.suc(ResponseCode.Code200,res);
    }


}




