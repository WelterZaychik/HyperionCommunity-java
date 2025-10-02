package xyz.Brownie.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.Brownie.constants.ResponseCode;
import xyz.Brownie.utils.Result;
import xyz.Brownie.bean.entity.User;
import xyz.Brownie.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/user")
@CrossOrigin
public class UserController {
    @Autowired
    private UserService userService;

    //登录
    // @PostMapping("/login")
    // public Result login(@RequestBody User user){
    //     return userService.login(user);
    // }

    //登录
    @PostMapping("/login")
    public Result login(@RequestParam("account") String account,@RequestParam("password") String password) {
        User user = new User();
        user.setAccount(account);
        user.setPassword(password);
        return userService.login(user);
    }

    //新建用户
    @PostMapping("/add")
    public Result add(@RequestBody User user){//创建前十个,直接给管理员
        return userService.addUser(user);
    }

    // 通过id查询user
    @GetMapping("/{id}")
    public User getUserById(@PathVariable("id") Long id){
        return userService.getById(id);
    }

    //通过id查询文章列表
    @GetMapping("/getTopicList/{id}")
    public Result TopicById(@PathVariable("id") Long id){
        return userService.TopicById(id);
    }

    //根据用户id查询用户的总观看数和总点赞数
    @GetMapping("/lvn/{id}")
    public Result LikeViewNum(@PathVariable("id")Long id){
        return userService.LikeViewNum(id);
    }

    //修改密码
    @PostMapping("/change")
    public Result change(@RequestBody User user){
        return userService.change(user);
    }

    //用户信息修改
    @PutMapping("/revise")
    public Result revise(@RequestBody User user){
        return userService.revise(user);
    }
    //登出

    @GetMapping("/logout/{account}")
    public Result logout(@PathVariable("account") String account){
        return userService.logout(account);
    }

    // 根据account查询用户信息
    @GetMapping("/info")
    public Result getUserInfo(@RequestParam("account") String account) {
        try {
            List<User> users = userService.list(Wrappers.<User>lambdaQuery().eq(User::getAccount, account));
            if (users != null && !users.isEmpty()) {
                User user = users.get(0);
                // 创建一个只包含需要信息的对象，避免返回敏感信息
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("account", user.getAccount());
                userInfo.put("name", user.getName());
                userInfo.put("avatar", user.getAvatar());
                return Result.suc(ResponseCode.Code200, userInfo);
            } else {
                return Result.fail(ResponseCode.Code404,null);
            }
        } catch (Exception e) {
            return Result.fail(ResponseCode.CodeDefault, null);
        }
    }

    /**
     * user-follows模块需求
     *
     */
    @PutMapping("/{userId}/adjust-follows")
    public Result adjustFollowsCount(@PathVariable Long userId, @RequestParam int delta) {
        userService.adjustFollowsCount(userId, delta);
        return Result.suc(ResponseCode.Code200);
    }

    @PutMapping("/{userId}/adjust-fans")
    public Result adjustFansCount(@PathVariable Long userId, @RequestParam int delta) {
        userService.adjustFansCount(userId, delta);
        return Result.suc(ResponseCode.Code200);
    }
}
