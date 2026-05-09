package com.ying.tech.community.service.admin.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminCategoryStatusUpdateReq {
    @NotNull(message = "状态不能为空")
    private Integer status;
}
