package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.core.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     * POST /user/register
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody UserSaveReq req) {
        // 调用 Service 逻辑
        Long userId = userService.register(req);
        // 返回统一格式
        return Result.success(userId);
    }

    /**
     * 用户登录接口
     * POST /user/login
     */
    @PostMapping("/login")
    public Result<String> login(@RequestParam String username, @RequestParam String password) {
        // 注意：这里为了简单直接用了 @RequestParam，正规做法是用 LoginReq DTO
        String token = userService.login(username, password);
        return Result.success(token);
    }

    /**
     * 获取当前登录用户的个人信息
     * 必须带 Token 才能访问
     */
    @GetMapping("/current")
    public Result<UserDO> getCurrentUser() {
        // 1. 直接从 ThreadLocal 拿 ID，不需要前端传 userId 参数！
        // 这就是拦截器的妙处：安全、隐形
        Long userId = ReqInfoContext.getReqInfo().getUserId();

        // 2. 查库
        UserDO user = userService.getUserInfo(userId);

        // 3. 脱敏 (把密码设为空，防止泄露)
        user.setPassword(null);

        return Result.success(user);
    }
}