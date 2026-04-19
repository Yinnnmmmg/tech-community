package com.ying.tech.community.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.service.user.vo.FollowActionVO;
import com.ying.tech.community.service.user.vo.FollowStatsVO;
import com.ying.tech.community.service.user.vo.UserFollowListItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户相关接口。
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<Long> register(@RequestBody UserSaveReq req) {
        Long userId = userService.register(req);
        return Result.success(userId);
    }

    /**
     * 用户登录接口。
     * POST /user/login
     */
    @PostMapping("/login")
    public Result<String> login(@RequestParam String username, @RequestParam String password) {
        String token = userService.login(username, password);
        return Result.success(token);
    }

    /**
     * 退出登录
     * */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/current")
    public Result<UserDO> getCurrentUser() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        UserDO user = userService.getUserInfo(userId);
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 关注用户接口。
     */
    @PostMapping("/follow")
    public Result<FollowActionVO> followUser(@RequestParam Long targetUserId) {
        return Result.success(userService.followUser(targetUserId));
    }

    /**
     * 取消关注用户接口。
     */
    @PostMapping("/unfollow")
    public Result<FollowActionVO> unfollowUser(@RequestParam Long targetUserId) {
        return Result.success(userService.unfollowUser(targetUserId));
    }

    /**
     * 查询关注状态接口。
     */
    @GetMapping("/follow/status")
    public Result<FollowActionVO> getFollowStatus(@RequestParam Long targetUserId) {
        return Result.success(userService.getFollowStatus(targetUserId));
    }

    /**
     * 查询用户关注统计接口。
     */
    @GetMapping("/{userId}/follow/stats")
    public Result<FollowStatsVO> getFollowStats(@PathVariable Long userId) {
        return Result.success(userService.getFollowStats(userId));
    }

    /**
     * 分页查询关注列表接口。
     */
    @GetMapping("/{userId}/follows")
    public Result<PageResult<UserFollowListItemVO>> getFollowList(@PathVariable Long userId,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getFollowList(userId, page, size));
    }

    /**
     * 分页查询粉丝列表接口。
     */
    @GetMapping("/{userId}/fans")
    public Result<PageResult<UserFollowListItemVO>> getFanList(@PathVariable Long userId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getFanList(userId, page, size));
    }
}
