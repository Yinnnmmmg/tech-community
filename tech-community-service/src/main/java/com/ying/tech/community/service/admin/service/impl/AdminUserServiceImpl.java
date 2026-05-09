package com.ying.tech.community.service.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.admin.service.AdminUserService;
import com.ying.tech.community.service.admin.vo.AdminUserListItemVO;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserInfoDO;
import com.ying.tech.community.service.user.repository.mapper.UserInfoMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    private static final int ADMIN_ROLE_CODE = 1;
    private static final int NORMAL_ROLE_CODE = 0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;

    public AdminUserServiceImpl(UserMapper userMapper, UserInfoMapper userInfoMapper) {
        this.userMapper = userMapper;
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public PageResult<AdminUserListItemVO> getUsers(String username, Integer userRole, Integer page, Integer size) {
        List<UserDO> users = userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                .like(StringUtils.hasText(username), UserDO::getUsername, username == null ? null : username.trim())
                .eq(userRole != null, UserDO::getUserRole, userRole)
                .orderByDesc(UserDO::getCreateTime)
                .orderByDesc(UserDO::getId));
        if (users == null || users.isEmpty()) {
            return new PageResult<>(0L, Collections.emptyList());
        }

        Map<Long, UserInfoDO> userInfoMap = userInfoMapper.selectList(new LambdaQueryWrapper<UserInfoDO>()
                        .in(UserInfoDO::getUserId, users.stream().map(UserDO::getId).toList()))
                .stream()
                .collect(Collectors.toMap(UserInfoDO::getUserId, Function.identity(), (left, right) -> left));

        List<AdminUserListItemVO> items = users.stream()
                .map(user -> toUserItem(user, userInfoMap.get(user.getId())))
                .toList();

        long total = items.size();
        int fromIndex = Math.max((page - 1) * size, 0);
        if (fromIndex >= items.size()) {
            return new PageResult<>(total, Collections.emptyList());
        }
        int toIndex = Math.min(fromIndex + size, items.size());
        return new PageResult<>(total, items.subList(fromIndex, toIndex));
    }

    @Override
    public void updateUserRole(Long userId, Integer userRole) {
        if (!Objects.equals(userRole, NORMAL_ROLE_CODE) && !Objects.equals(userRole, ADMIN_ROLE_CODE)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(StatusEnum.USER_NOT_FOUND);
        }

        Integer previousRole = user.getUserRole() == null ? NORMAL_ROLE_CODE : user.getUserRole();
        if (Objects.equals(previousRole, ADMIN_ROLE_CODE) && Objects.equals(userRole, NORMAL_ROLE_CODE)) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getUserRole, ADMIN_ROLE_CODE));
            if (adminCount != null && adminCount <= 1) {
                throw new BusinessException(StatusEnum.LAST_ADMIN_REQUIRED);
            }
        }

        user.setUserRole(userRole);
        userMapper.updateById(user);

        UserInfoDO userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfoDO>()
                .eq(UserInfoDO::getUserId, userId)
                .last("limit 1"));
        if (userInfo != null && !StringUtils.hasText(userInfo.getUsername())) {
            userInfo.setUsername(user.getUsername());
            userInfoMapper.updateById(userInfo);
        }
    }

    private AdminUserListItemVO toUserItem(UserDO user, UserInfoDO userInfo) {
        AdminUserListItemVO item = new AdminUserListItemVO();
        item.setUserId(user.getId());
        item.setUsername(userInfo != null && StringUtils.hasText(userInfo.getUsername()) ? userInfo.getUsername() : user.getUsername());
        item.setUserRole(user.getUserRole() == null ? NORMAL_ROLE_CODE : user.getUserRole());
        if (userInfo != null) {
            item.setPhoto(userInfo.getPhoto());
            item.setPosition(userInfo.getPosition());
            item.setCompany(userInfo.getCompany());
            item.setProfile(userInfo.getProfile());
        }
        item.setCreateTime(formatTime(user.getCreateTime()));
        return item;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
