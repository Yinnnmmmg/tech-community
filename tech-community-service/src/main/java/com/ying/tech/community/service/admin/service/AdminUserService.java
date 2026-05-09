package com.ying.tech.community.service.admin.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.admin.vo.AdminUserListItemVO;

public interface AdminUserService {
    PageResult<AdminUserListItemVO> getUsers(String username, Integer userRole, Integer page, Integer size);

    void updateUserRole(Long userId, Integer userRole);
}
