package com.ying.tech.community.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.user.req.ChangePasswordReq;
import com.ying.tech.community.service.user.req.PhoneLoginReq;
import com.ying.tech.community.service.user.req.SendSmsReq;
import com.ying.tech.community.service.user.req.UserProfileUpdateReq;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.service.user.vo.FollowActionVO;
import com.ying.tech.community.service.user.vo.FollowStatsVO;
import com.ying.tech.community.service.user.vo.UserCurrentVO;
import com.ying.tech.community.service.user.vo.UserFollowListItemVO;
import com.ying.tech.community.service.user.vo.UserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<Long> register(@RequestBody UserSaveReq req) {
        return Result.success(userService.register(req));
    }

    @PostMapping("/login")
    public Result<String> login(@RequestParam String username, @RequestParam String password) {
        return Result.success(userService.login(username, password));
    }

    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestBody SendSmsReq req) {
        userService.sendSmsCode(req.getPhone());
        return Result.success();
    }

    @PostMapping("/login/phone")
    public Result<String> loginByPhone(@RequestBody PhoneLoginReq req) {
        return Result.success(userService.loginByPhone(req.getPhone(), req.getPassword(), req.getSmsCode()));
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    @SaCheckLogin
    @GetMapping("/current")
    public Result<UserCurrentVO> getCurrentUser() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        return Result.success(userService.getCurrentUser(userId));
    }

    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Long userId) {
        return Result.success(userService.getUserProfile(userId));
    }

    @SaCheckLogin
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordReq req) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        userService.changePassword(userId, req);
        return Result.success();
    }

    @SaCheckLogin
    @PutMapping("/profile")
    public Result<Void> updateCurrentUserProfile(@RequestBody UserProfileUpdateReq req) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        userService.updateCurrentUserProfile(userId, req);
        return Result.success();
    }

    @GetMapping("/{userId}/articles")
    public Result<PageResult<ArticleListVO>> getUserArticles(@PathVariable Long userId,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getUserArticlePage(userId, page, size));
    }

    @SaCheckLogin
    @GetMapping("/{userId}/collections/articles")
    public Result<PageResult<ArticleListVO>> getUserCollectionArticles(@PathVariable Long userId,
                                                                       @RequestParam(defaultValue = "1") Integer page,
                                                                       @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getUserCollectionArticlePage(userId, page, size));
    }

    @SaCheckLogin
    @GetMapping("/{userId}/likes/articles")
    public Result<PageResult<ArticleListVO>> getUserLikeArticles(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getUserLikeArticlePage(userId, page, size));
    }

    @SaCheckLogin
    @PostMapping("/follow")
    public Result<FollowActionVO> followUser(@RequestParam Long targetUserId) {
        return Result.success(userService.followUser(targetUserId));
    }

    @SaCheckLogin
    @PostMapping("/unfollow")
    public Result<FollowActionVO> unfollowUser(@RequestParam Long targetUserId) {
        return Result.success(userService.unfollowUser(targetUserId));
    }

    @SaCheckLogin
    @GetMapping("/follow/status")
    public Result<FollowActionVO> getFollowStatus(@RequestParam Long targetUserId) {
        return Result.success(userService.getFollowStatus(targetUserId));
    }

    @GetMapping("/{userId}/follow/stats")
    public Result<FollowStatsVO> getFollowStats(@PathVariable Long userId) {
        return Result.success(userService.getFollowStats(userId));
    }

    @GetMapping("/{userId}/follows")
    public Result<PageResult<UserFollowListItemVO>> getFollowList(@PathVariable Long userId,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getFollowList(userId, page, size));
    }

    @GetMapping("/{userId}/fans")
    public Result<PageResult<UserFollowListItemVO>> getFanList(@PathVariable Long userId,
                                                               @RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.getFanList(userId, page, size));
    }
}
