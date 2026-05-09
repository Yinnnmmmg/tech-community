package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.article.service.ArticleCategoryService;
import com.ying.tech.community.service.article.vo.ArticleCategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/article/categories")
public class ArticleCategoryController {
    private final ArticleCategoryService articleCategoryService;

    public ArticleCategoryController(ArticleCategoryService articleCategoryService) {
        this.articleCategoryService = articleCategoryService;
    }

    @GetMapping
    public Result<List<ArticleCategoryVO>> listEnabledCategories() {
        return Result.success(articleCategoryService.listEnabledCategories());
    }
}
