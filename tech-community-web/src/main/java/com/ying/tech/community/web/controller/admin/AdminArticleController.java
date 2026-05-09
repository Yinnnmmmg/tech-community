package com.ying.tech.community.web.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.admin.service.AdminArticleService;
import com.ying.tech.community.service.admin.vo.AdminArticleDetailVO;
import com.ying.tech.community.service.admin.vo.AdminArticleListItemVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/articles")
@SaCheckRole("admin")
public class AdminArticleController {
    private final AdminArticleService adminArticleService;

    public AdminArticleController(AdminArticleService adminArticleService) {
        this.adminArticleService = adminArticleService;
    }

    @GetMapping
    public Result<PageResult<AdminArticleListItemVO>> getArticles(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Integer status,
                                                                  @RequestParam(required = false) Long categoryId,
                                                                  @RequestParam(required = false) String authorName,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(adminArticleService.getArticles(keyword, status, categoryId, authorName, page, size));
    }

    @GetMapping("/{articleId}")
    public Result<AdminArticleDetailVO> getArticleDetail(@PathVariable Long articleId) {
        return Result.success(adminArticleService.getArticleDetail(articleId));
    }

    @DeleteMapping("/{articleId}")
    public Result<Boolean> deleteArticle(@PathVariable Long articleId) {
        adminArticleService.deleteArticle(articleId);
        return Result.success(Boolean.TRUE);
    }
}
