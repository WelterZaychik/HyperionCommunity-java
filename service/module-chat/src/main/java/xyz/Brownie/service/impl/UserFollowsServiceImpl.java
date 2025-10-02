package xyz.Brownie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import xyz.Brownie.bean.dto.UserFollowsDto;
import xyz.Brownie.bean.entity.User;
import xyz.Brownie.bean.entity.UserFollows;
import xyz.Brownie.bean.vo.FollowsVo;
import xyz.Brownie.constants.Constants;
import xyz.Brownie.constants.ResponseCode;
import xyz.Brownie.utils.BeanCopyUtils;
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

    // 核心状态常量
    private static final Integer STATUS_NOT_DELETED = 0;
    private static final Integer STATUS_DELETED = 1;

    // 常用日志消息前缀
    private static final String LOG_PREFIX_PARAM_EMPTY = "操作参数为空: userId={}, followsId={}";
    private static final String LOG_PREFIX_USER_ACTION = "用户[{}]{}用户[{}]";
    @Override
    public Result getFollowsListByUserId(Long userId) {
        Map<String, Object> res = new HashMap<>();
        if (userId == null || userId <= 0) {
            log.warn("Invalid userId parameter: {}", userId);
            res.put("msg", "参数传递错误");
            return Result.fail(ResponseCode.CodeDefault, res);
        }

        try {
            List<User> friendships = userFollowsMapper.getFollowsListByUserId(userId);
            List<FollowsVo> followsVos = BeanCopyUtils.copyBeanList(friendships, FollowsVo.class);
            res.put("followsList", followsVos);
            return Result.suc(ResponseCode.Code200, res);
        } catch (Exception e) {
            log.error("获取关注列表失败，userId: {}", userId, e);
            res.put("msg", "获取关注列表失败");
            return Result.fail(ResponseCode.CodeDefault, res);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addFollows(UserFollowsDto userFollowsDto) {
        Map<String, Object> res = new HashMap<>();
        try {
            // 1. 参数校验
            if (userFollowsDto.getUserId() == null || userFollowsDto.getFollowsId() == null) {
                log.warn("关注" + LOG_PREFIX_PARAM_EMPTY, 
                         userFollowsDto.getUserId(), userFollowsDto.getFollowsId());
                res.put("msg", "参数传递错误");
                return Result.fail(ResponseCode.CodeDefault, res);
            }

            // 2. 不能关注自己
            if (userFollowsDto.getUserId().equals(userFollowsDto.getFollowsId())) {
                log.warn("用户尝试关注自己: userId={}", userFollowsDto.getUserId());
                res.put("msg", "不能关注自己");
                return Result.fail(ResponseCode.CodeDefault, res);
            }

            Long userId = userFollowsDto.getUserId();
            Long followsId = userFollowsDto.getFollowsId();
            log.info(LOG_PREFIX_USER_ACTION, userId, "关注", followsId);

            // 3. 检查是否已存在关注关系
            LambdaQueryWrapper<UserFollows> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserFollows::getUserId, userId)
                    .eq(UserFollows::getFollowsId, followsId);
            UserFollows userFollows = userFollowsMapper.selectOne(wrapper);

            // 4. 如果记录已存在
            if (userFollows != null) {
                if (userFollows.getIsDelete() == STATUS_DELETED) {
                    // 如果是已删除状态，恢复关注
                    userFollows.setIsDelete(STATUS_NOT_DELETED);
                    userFollows.setStatus(Constants.FOLLOWS_STATUS_ZERO); // 重置为单方关注状态
                    userFollowsMapper.updateById(userFollows);
                } else {
                    log.info(LOG_PREFIX_USER_ACTION, userId, "已关注", followsId);
                    res.put("msg", "已关注该用户");
                    return Result.fail(ResponseCode.CodeDefault, res);
                }
            } else {
                // 5. 创建新的关注记录
                userFollows = new UserFollows();
                userFollows.setUserId(userId);
                userFollows.setFollowsId(followsId);
                userFollows.setStatus(Constants.FOLLOWS_STATUS_ZERO); // 初始状态为单方关注
                userFollows.setIsDelete(STATUS_NOT_DELETED);
                userFollowsMapper.insert(userFollows);
            }

            // 6. 检查是否形成互相关注
            LambdaQueryWrapper<UserFollows> reverseWrapper = new LambdaQueryWrapper<>();
            reverseWrapper.eq(UserFollows::getUserId, followsId)
                    .eq(UserFollows::getFollowsId, userId)
                    .eq(UserFollows::getIsDelete, STATUS_NOT_DELETED);
            UserFollows reverseFollow = userFollowsMapper.selectOne(reverseWrapper);

            if (reverseFollow != null) {
                // 7. 如果对方也关注了我，更新为互相关注状态
                userFollows.setStatus(Constants.FOLLOWS_STATUS_ONE);
                userFollowsMapper.updateById(userFollows);

                reverseFollow.setStatus(Constants.FOLLOWS_STATUS_ONE);
                userFollowsMapper.updateById(reverseFollow);
            }

            // 8. 更新用户表中的关注数和粉丝数
            userCountService.incrementFollowsCount(userId);
            userCountService.incrementFansCount(followsId);

            res.put("msg", "关注成功");
            return Result.suc(ResponseCode.Code200, res);
        } catch (Exception e) {
            log.error("关注操作失败: {}", e.getMessage(), e);
            res.put("msg", "关注操作失败");
            return Result.fail(ResponseCode.CodeDefault, res);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result removeFollows(UserFollowsDto userFollowsDto) {
        Map<String, Object> res = new HashMap<>();
        try {
            // 1. 参数校验
            if (userFollowsDto.getUserId() == null || userFollowsDto.getFollowsId() == null) {
                log.warn("取消关注" + LOG_PREFIX_PARAM_EMPTY, 
                         userFollowsDto.getUserId(), userFollowsDto.getFollowsId());
                res.put("msg", "参数传递错误");
                return Result.fail(ResponseCode.CodeDefault, res);
            }

            if (userFollowsDto.getUserId().equals(userFollowsDto.getFollowsId())) {
                log.warn("用户尝试操作自己: userId={}", userFollowsDto.getUserId());
                res.put("msg", "不能操作自己哦");
                return Result.fail(ResponseCode.CodeDefault, res);
            }

            Long userId = userFollowsDto.getUserId();
            Long followsId = userFollowsDto.getFollowsId();
            log.info(LOG_PREFIX_USER_ACTION, userId, "取消关注", followsId);

            // 2. 检查是否存在关注关系
            LambdaQueryWrapper<UserFollows> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserFollows::getUserId, userId)
                    .eq(UserFollows::getFollowsId, followsId)
                    .eq(UserFollows::getIsDelete, STATUS_NOT_DELETED);
            UserFollows userFollows = userFollowsMapper.selectOne(wrapper);

            // 3. 如果关注关系不存在
            if (userFollows == null) {
                log.info(LOG_PREFIX_USER_ACTION, userId, "未关注", followsId);
                res.put("msg", "未关注该用户");
                return Result.fail(ResponseCode.CodeDefault, res);
            }

            // 4. 设置关注关系为删除状态
            userFollows.setIsDelete(STATUS_DELETED);
            userFollowsMapper.updateById(userFollows);

            // 5. 检查是否存在互相关注关系
            LambdaQueryWrapper<UserFollows> reverseWrapper = new LambdaQueryWrapper<>();
            reverseWrapper.eq(UserFollows::getUserId, followsId)
                    .eq(UserFollows::getFollowsId, userId)
                    .eq(UserFollows::getIsDelete, STATUS_NOT_DELETED);
            UserFollows reverseFollow = userFollowsMapper.selectOne(reverseWrapper);

            // 6. 如果对方也关注了我，更新对方的关注状态为单方关注
            if (reverseFollow != null && reverseFollow.getStatus() == Constants.FOLLOWS_STATUS_ONE) {
                reverseFollow.setStatus(Constants.FOLLOWS_STATUS_ZERO);
                userFollowsMapper.updateById(reverseFollow);
            }

            // 7. 更新用户表中的关注数和粉丝数
            userCountService.decrementFollowsCount(userId);
            userCountService.decrementFansCount(followsId);

            res.put("msg", "取消关注成功");
            return Result.suc(ResponseCode.Code200, res);
        } catch (Exception e) {
            log.error("取消关注操作失败: {}", e.getMessage(), e);
            res.put("msg", "取消关注操作失败");
            return Result.fail(ResponseCode.CodeDefault, res);
        }
    }


}




