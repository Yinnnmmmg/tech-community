package com.ying.tech.community.web.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.admin.service.AdminDashboardService;
import com.ying.tech.community.service.admin.vo.AdminDashboardSummaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@SaCheckRole("admin")
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    public Result<AdminDashboardSummaryVO> getSummary() {
        return Result.success(adminDashboardService.getSummary());
    }
}
