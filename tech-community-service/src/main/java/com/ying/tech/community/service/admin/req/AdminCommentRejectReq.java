package com.ying.tech.community.service.admin.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCommentRejectReq {
    @NotBlank(message = "驳回原因不能为空")
    private String reason;
}
