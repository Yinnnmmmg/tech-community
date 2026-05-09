package com.ying.tech.community.web.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.admin.req.AdminCommentRejectReq;
import com.ying.tech.community.service.admin.service.AdminCommentService;
import com.ying.tech.community.service.admin.vo.AdminCommentListItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/comments")
@SaCheckRole("admin")
public class AdminCommentController {
    private final AdminCommentService adminCommentService;

    public AdminCommentController(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
    }

    @GetMapping
    public Result<PageResult<AdminCommentListItemVO>> getComments(@RequestParam(required = false) Integer status,
                                                                  @RequestParam(required = false) Long articleId,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(adminCommentService.getComments(status, articleId, keyword, page, size));
    }

    @PostMapping("/{commentId}/approve")
    public Result<Boolean> approveComment(@PathVariable Long commentId) {
        adminCommentService.approveComment(commentId);
        return Result.success(Boolean.TRUE);
    }

    @PostMapping("/{commentId}/reject")
    public Result<Boolean> rejectComment(@PathVariable Long commentId, @Valid @RequestBody AdminCommentRejectReq req) {
        adminCommentService.rejectComment(commentId, req.getReason());
        return Result.success(Boolean.TRUE);
    }

    @DeleteMapping("/{commentId}")
    public Result<Boolean> deleteComment(@PathVariable Long commentId) {
        adminCommentService.deleteComment(commentId);
        return Result.success(Boolean.TRUE);
    }
}
