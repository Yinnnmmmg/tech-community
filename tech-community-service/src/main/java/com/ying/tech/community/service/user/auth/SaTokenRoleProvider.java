package com.ying.tech.community.service.user.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SaTokenRoleProvider implements StpInterface {
    private static final int ADMIN_ROLE_CODE = 1;

    private final UserMapper userMapper;

    public SaTokenRoleProvider(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId;
        try {
            userId = Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException exception) {
            return Collections.emptyList();
        }

        UserDO user = userMapper.selectById(userId);
        if (user != null && user.getUserRole() != null && user.getUserRole() == ADMIN_ROLE_CODE) {
            return List.of("admin");
        }
        return Collections.emptyList();
    }
}
