package com.ying.tech.community.service.article.service;

import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;


public interface ArticleService {
    Long publishArticle(ArticlePostReq articlePostReq);

    CursorPageResult<ArticleListVO> getArticleList(Long cursor, Integer pageSize);

    ArticleLikeVO likeArticle(Long articleId);
}