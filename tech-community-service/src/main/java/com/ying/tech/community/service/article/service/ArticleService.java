package com.ying.tech.community.service.article.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.vo.ArticleListVO;

import java.util.List;

public interface ArticleService {
    Long publishArticle(ArticlePostReq articlePostReq);

    PageResult<ArticleListVO> getArticleList(Integer pageNum, Integer pageSize);
}