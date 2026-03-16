package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;
    @Autowired
    private ArticleDetailService articleDetailService;

    /**
     * 点赞接口
     * POST /article/like
     * param: articleId 文章ID
     * */
    @PostMapping("/like")
    public Result<ArticleLikeVO> likeArticle(@RequestParam Long articleId) {
        ArticleLikeVO articleLikeVO = articleService.likeArticle(articleId);
        return Result.success(articleLikeVO);
    }


    /**
     * 发布文章接口
     * POST /article/publish
     */
    @PostMapping("/publish")
    public Result<Long> publishArticle(@Validated @RequestBody ArticlePostReq articlePostReq) {

        Long articleId = articleService.publishArticle(articlePostReq);
        return Result.success(articleId);
    }

    /**
     * 查询文章列表接口
     * GET /article/list
     * */
    @GetMapping("/list")
    public Result<PageResult<ArticleListVO>> articleList(@RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<ArticleListVO> articlesPageResult = articleService.getArticleList(pageNum, pageSize);
        return Result.success(articlesPageResult);
    }


    /**
     * 查询文章详情接口
     * 根据文章ID查询文章详情接口
     * GET /article/detail/{articleId}
     * */
    @GetMapping("/detail/{articleId}")
    public Result<ArticleDetailVO> articleDetail(@PathVariable Long articleId) {
        ArticleDetailVO articleDetailVO = articleDetailService.getArticleDetailById(articleId);
        return Result.success(articleDetailVO);
    }
}