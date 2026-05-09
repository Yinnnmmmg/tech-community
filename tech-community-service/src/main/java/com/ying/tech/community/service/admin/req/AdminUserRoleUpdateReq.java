package com.ying.tech.community.service.admin.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserRoleUpdateReq {
    @NotNull(message = "角色不能为空")
    private Integer userRole;
}
