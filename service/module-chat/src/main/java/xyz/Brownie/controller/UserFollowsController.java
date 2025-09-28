package xyz.Brownie.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.Brownie.bean.dto.UserFollowsDto;
import xyz.Brownie.utils.Result;
import xyz.Brownie.service.UserFollowsService;

/**
 * 好友关系管理Controller
 * @author AI Assistant
 */
@RestController
@RequestMapping("/friendships")
@CrossOrigin
public class UserFollowsController {

    @Autowired
    private UserFollowsService userFollowsService;

    /**
     * 添加关注关系
     * @param userFollows 好友关系对象
     * @return 操作结果
     */
    @PostMapping
    public Result addFollows(@RequestBody UserFollowsDto userFollows) {


        return userFollowsService.addFollows(userFollows);

    }

    /**
     * 根据ID删除好友关系
     * @param id 好友关系ID
     * @return 操作结果
     */
//    @DeleteMapping("/{id}")
//    public Result deleteFriendship(@PathVariable Long id) {
//
//        boolean remove = friendshipsService.removeById(id);
//        if (remove) {
//            return Result.suc(200, "删除好友关系成功");
//        } else {
//            return Result.fail(402, "删除好友关系失败");
//        }
//    }

    /**
     * 更新好友关系
     * @param friendships 好友关系对象
     * @return 操作结果
     */
//    @PutMapping
//    public Result updateFriendship(@RequestBody Friendships friendships) {
//        boolean update = friendshipsService.updateById(friendships);
//        if (update) {
//            return Result.suc(200, "更新好友关系成功");
//        } else {
//            return Result.fail(402, "更新好友关系失败");
//        }
//    }

    /**
     * 根据ID查询好友关系
     * @param id 好友关系ID
     * @return 好友关系对象
     */
//    @GetMapping("/{id}")
//    public Result getFriendshipById(@PathVariable Long id) {
//        Friendships friendship = friendshipsService.getById(id);
//        return Result.suc(200, friendship);
//    }

    /**
     * 查询所有好友关系列表
     * @return 好友关系列表
     */
    @GetMapping("/{id}")
    public Result getFollowsList(@PathVariable Long id) {

        return userFollowsService.getFollowsListByUserId(id);


    }

    /**
     * 根据用户ID查询好友列表
     * @param userId 用户ID
     * @return 好友列表
     */
//    @GetMapping("/user/{userId}")
//    public Result getFriendshipsByUserId(@PathVariable Long userId) {
//        List<Friendships> list = friendshipsService.lambdaQuery()
//                .eq(Friendships::getUserId, userId)
//                .eq(Friendships::getIsDelete, 0)
//                .list();
//        return Result.suc(200, list);
//    }
}