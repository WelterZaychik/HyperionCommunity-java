package xyz.Brownie.mapper;

import org.apache.ibatis.annotations.Mapper;
import xyz.Brownie.bean.entity.User;
import xyz.Brownie.bean.entity.UserFollows;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author Welt
* @description 针对表【user_follows】的数据库操作Mapper
* @createDate 2025-09-27 15:01:59
* @Entity xyz.Brownie.bean.entity.UserFollows
*/
@Mapper
public interface UserFollowsMapper extends BaseMapper<UserFollows> {

    List<User> getFollowsListByUserId(Long userId);
}




