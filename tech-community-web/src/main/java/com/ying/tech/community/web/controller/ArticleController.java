package com.ying.tech.community.web.controller;

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
import org.springframework.web.bind.annotation.*;


/**
 * 文章相关接口。
 */
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


    /**
     * ES 全文高亮检索接口
     * GET /article/search
     * @param keyWord 搜索关键词
     * @param page 页码（从 1 开始，默认 1）
     * @param size 每页大小（默认 10）
     * @return 带高亮结果的搜索列表
     */
    @GetMapping("/search")
    public Result<PageResult<ArticleSearchHighlightVO>> searchWithHighlight(
            @RequestParam String keyWord,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<ArticleSearchHighlightVO> searchPage = articleSearchService
            .searchWithHighlight(keyWord, page, size);

        PageResult<ArticleSearchHighlightVO> pageResult = new PageResult<>(
            searchPage.getTotal(),
            searchPage.getRecords()
        );
        return Result.success(pageResult);
    }

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
     * 收藏接口。
     * POST /article/collect
     *
     * @param articleId 文章 ID
     * @return 收藏结果
     */
    @PostMapping("/collect")
    public Result<ArticleCollectVO> collectArticle(@RequestParam Long articleId) {
        ArticleCollectVO articleCollectVO = articleService.collectArticle(articleId);
        return Result.success(articleCollectVO);
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
     * 更新文章接口。
     * PUT /article/{articleId}
     *
     * @param articleId         文章 ID
     * @param articleUpdateReq  更新参数
     * @return 更新后的文章 ID
     */
    @PutMapping("/{articleId}")
    public Result<Long> updateArticle(@PathVariable Long articleId,
                                      @Validated @RequestBody ArticleUpdateReq articleUpdateReq) {
        Long updatedArticleId = articleService.updateArticle(articleId, articleUpdateReq);
        return Result.success(updatedArticleId);
    }

    /**
     * 删除文章接口。
     * DELETE /article/{articleId}
     *
     * @param articleId 文章 ID
     * @return 删除结果
     */
    @DeleteMapping("/{articleId}")
    public Result<Boolean> deleteArticle(@PathVariable Long articleId) {
        articleService.deleteArticle(articleId);
        return Result.success(Boolean.TRUE);
    }

    /**
     * 查询文章列表接口
     * GET /article/list
     * */
    @GetMapping("/list")
    public Result<CursorPageResult<ArticleListVO>> articleList(@RequestParam(defaultValue = "1") Long cursor,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        CursorPageResult<ArticleListVO> articlesPageResult = articleService.getArticleList(cursor, pageSize);
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
