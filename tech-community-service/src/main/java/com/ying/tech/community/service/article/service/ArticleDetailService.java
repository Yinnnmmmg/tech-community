package com.ying.tech.community.service.article.service;

import com.ying.tech.community.service.article.vo.ArticleDetailVO;

public interface ArticleDetailService {
    ArticleDetailVO getArticleDetailById(Long articleId);
}
