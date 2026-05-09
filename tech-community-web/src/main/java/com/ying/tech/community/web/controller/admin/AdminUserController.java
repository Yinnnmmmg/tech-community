package com.ying.tech.community.web.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.admin.req.AdminUserRoleUpdateReq;
import com.ying.tech.community.service.admin.service.AdminUserService;
import com.ying.tech.community.service.admin.vo.AdminUserListItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@SaCheckRole("admin")
public class AdminUserController {
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Result<PageResult<AdminUserListItemVO>> getUsers(@RequestParam(required = false) String username,
                                                            @RequestParam(required = false) Integer userRole,
                                                            @RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(adminUserService.getUsers(username, userRole, page, size));
    }

    @PatchMapping("/{userId}/role")
    public Result<Boolean> updateRole(@PathVariable Long userId, @Valid @RequestBody AdminUserRoleUpdateReq req) {
        adminUserService.updateUserRole(userId, req.getUserRole());
        return Result.success(Boolean.TRUE);
    }
}
