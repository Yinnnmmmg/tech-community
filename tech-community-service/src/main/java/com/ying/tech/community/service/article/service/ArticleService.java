package com.ying.tech.community.service.article.service;

import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.req.ArticleUpdateReq;
import com.ying.tech.community.service.article.vo.ArticleCollectVO;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;

import java.util.List;

public interface ArticleService {
    Long publishArticle(ArticlePostReq articlePostReq);

    Long updateArticle(Long articleId, ArticleUpdateReq articleUpdateReq);

    void deleteArticle(Long articleId);

    void deleteArticleByAdmin(Long articleId);

    CursorPageResult<ArticleListVO> getArticleList(Long cursor, Integer pageSize, Long categoryId, Boolean followedOnly);

    PageResult<ArticleListVO> getApprovedArticlesByUser(Long userId, Integer page, Integer size);

    List<ArticleListVO> getApprovedArticlesByIds(List<Long> articleIds);

    ArticleLikeVO likeArticle(Long articleId);

    ArticleCollectVO collectArticle(Long articleId);
}
