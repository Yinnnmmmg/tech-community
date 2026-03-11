package com.ying.tech.community.service.user.service;

import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.entity.UserDO;

public interface UserService {
    /**
     * 根据用户名获取用户信息
     */
    UserDO getByUsername(String username);

    /**
     * 用户注册
     * @param req 前端传来的参数
     * @return 注册成功的用户ID
     */
    Long register(UserSaveReq req);

    /**
     * 用户登录
     * @param username 用户名
     *  @param  password  密码
      * @return 生成的 Token
     * */
    String login(String username, String password);

    /**
     * 获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    UserDO getUserInfo(Long userId);
}