package com.ying.tech.community.web.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.admin.req.AdminCategorySaveReq;
import com.ying.tech.community.service.admin.req.AdminCategoryStatusUpdateReq;
import com.ying.tech.community.service.admin.service.AdminCategoryService;
import com.ying.tech.community.service.admin.vo.AdminCategoryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@SaCheckRole("admin")
public class AdminCategoryController {
    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @GetMapping
    public Result<List<AdminCategoryVO>> getCategories() {
        return Result.success(adminCategoryService.getCategories());
    }

    @PostMapping
    public Result<AdminCategoryVO> createCategory(@Valid @RequestBody AdminCategorySaveReq req) {
        return Result.success(adminCategoryService.createCategory(req));
    }

    @PutMapping("/{categoryId}")
    public Result<AdminCategoryVO> updateCategory(@PathVariable Long categoryId,
                                                  @Valid @RequestBody AdminCategorySaveReq req) {
        return Result.success(adminCategoryService.updateCategory(categoryId, req));
    }

    @PatchMapping("/{categoryId}/status")
    public Result<Boolean> updateCategoryStatus(@PathVariable Long categoryId,
                                                @Valid @RequestBody AdminCategoryStatusUpdateReq req) {
        adminCategoryService.updateCategoryStatus(categoryId, req.getStatus());
        return Result.success(Boolean.TRUE);
    }
}
