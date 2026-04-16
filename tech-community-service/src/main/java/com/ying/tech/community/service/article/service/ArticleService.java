package com.ying.tech.community.service.article.service;

import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.req.ArticleUpdateReq;
import com.ying.tech.community.service.article.vo.ArticleCollectVO;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;


/**
 * 文章领域服务。
 */
public interface ArticleService {
    /**
     * 发布文章。
     *
     * @param articlePostReq 发布请求参数
     * @return 新生成的文章 ID
     */
    Long publishArticle(ArticlePostReq articlePostReq);

    /**
     * 更新文章内容并重新提交审核。
     *
     * @param articleId         文章 ID
     * @param articleUpdateReq  更新请求参数
     * @return 文章 ID
     */
    Long updateArticle(Long articleId, ArticleUpdateReq articleUpdateReq);

    /**
     * 删除文章。
     *
     * @param articleId 文章 ID
     */
    void deleteArticle(Long articleId);

    /**
     * 游标分页查询文章列表。
     *
     * @param cursor   上一页游标
     * @param pageSize 每页条数
     * @return 文章列表及下一页游标
     */
    CursorPageResult<ArticleListVO> getArticleList(Long cursor, Integer pageSize);

    /**
     * 点赞或取消点赞文章。
     *
     * @param articleId 文章 ID
     * @return 点赞结果
     */
    ArticleLikeVO likeArticle(Long articleId);

    /**
     * 收藏或取消收藏文章。
     *
     * @param articleId 文章 ID
     * @return 收藏结果
     */
    ArticleCollectVO collectArticle(Long articleId);
}
