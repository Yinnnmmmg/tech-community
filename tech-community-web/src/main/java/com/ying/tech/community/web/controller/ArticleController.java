package com.ying.tech.community.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.req.ArticleUpdateReq;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.service.ArticleSearchService;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleCollectVO;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.article.vo.ArticleSearchHighlightVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;
    @Autowired
    private ArticleDetailService articleDetailService;
    @Autowired
    private ArticleSearchService articleSearchService;

    @GetMapping("/search")
    public Result<PageResult<ArticleSearchHighlightVO>> searchWithHighlight(
            @RequestParam String keyWord,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<ArticleSearchHighlightVO> searchPage = articleSearchService.searchWithHighlight(keyWord, page, size);
        PageResult<ArticleSearchHighlightVO> pageResult = new PageResult<>(searchPage.getTotal(), searchPage.getRecords());
        return Result.success(pageResult);
    }

    @SaCheckLogin
    @PostMapping("/like")
    public Result<ArticleLikeVO> likeArticle(@RequestParam Long articleId) {
        ArticleLikeVO articleLikeVO = articleService.likeArticle(articleId);
        return Result.success(articleLikeVO);
    }

    @SaCheckLogin
    @PostMapping("/collect")
    public Result<ArticleCollectVO> collectArticle(@RequestParam Long articleId) {
        ArticleCollectVO articleCollectVO = articleService.collectArticle(articleId);
        return Result.success(articleCollectVO);
    }

    @SaCheckLogin
    @PostMapping("/publish")
    public Result<Long> publishArticle(@Validated @RequestBody ArticlePostReq articlePostReq) {
        Long articleId = articleService.publishArticle(articlePostReq);
        return Result.success(articleId);
    }

    @SaCheckLogin
    @PutMapping("/{articleId}")
    public Result<Long> updateArticle(@PathVariable Long articleId,
                                      @Validated @RequestBody ArticleUpdateReq articleUpdateReq) {
        Long updatedArticleId = articleService.updateArticle(articleId, articleUpdateReq);
        return Result.success(updatedArticleId);
    }

    @SaCheckLogin
    @DeleteMapping("/{articleId}")
    public Result<Boolean> deleteArticle(@PathVariable Long articleId) {
        articleService.deleteArticle(articleId);
        return Result.success(Boolean.TRUE);
    }

    @GetMapping("/list")
    public Result<CursorPageResult<ArticleListVO>> articleList(@RequestParam(defaultValue = "0") Long cursor,
                                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                                               @RequestParam(required = false) Long categoryId) {
        CursorPageResult<ArticleListVO> articlesPageResult = articleService.getArticleList(cursor, pageSize, categoryId);
        return Result.success(articlesPageResult);
    }

    @GetMapping("/detail/{articleId}")
    public Result<ArticleDetailVO> articleDetail(@PathVariable Long articleId) {
        ArticleDetailVO articleDetailVO = articleDetailService.getArticleDetailById(articleId);
        return Result.success(articleDetailVO);
    }
}
