package com.ying.tech.community.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.utils.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDO getByUsername(String username) {
        // 使用 MyBatis-Plus 的 Lambda 查询，防止字段名写错
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
    }

    @Override
    public Long register(UserSaveReq req) {
        // 1. 参数基本校验
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new RuntimeException("用户名或密码不能为空");
        }

        // 2. 检查用户名是否重复 (复用你之前写的查询方法)
        UserDO existUser = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, req.getUsername()));
        if (existUser != null) {
            throw new BusinessException(StatusEnum.USER_EXISTS);
        }

        // 3. 密码加密 (这里使用 Spring 自带的 MD5 工具)
        // 比如：123456 -> e10adc3949ba59abbe56e057f20f883e
        String encodedPwd = DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8));

        // 4. 封装对象
        UserDO user = new UserDO();
        user.setUsername(req.getUsername()); // 设置用户名
        user.setPassword(encodedPwd); // 设置加密后的密码
        user.setThirdAccountId(null); // 第三方账号 ID，如果是第三方登录才需要设置
        user.setLoginType(0); // 登录类型：0-普通登录，1-第三方登录
        user.setDeleted(0); // 0-未删除，1-已删除

        // 5. 入库
        userMapper.insert(user);

        // MyBatis-Plus 会自动把生成的 ID 回填到对象里
        return user.getId();
    }

    @Override
    public String login(String username, String password) {
        // 1. 判空
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("用户名或密码不能为空");
        }

        // 2. 查用户
        UserDO user = getByUsername(username);
        if (user == null) {
            throw new BusinessException(StatusEnum.USER_NOT_FOUND);
        }

        // 3. 比对密码 (前端传来的密码加密后 == 数据库里的密码)
        String inputPwdEncoded = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        if (!inputPwdEncoded.equals(user.getPassword())) {
            throw new BusinessException(StatusEnum.USER_PWD_ERROR);
        }

        // 4. 生成 Token
        return JWTUtils.createToken(user.getId());
    }

    @Override
    public UserDO getUserInfo(Long userId) {
        return userMapper.selectById(userId);
    }

}