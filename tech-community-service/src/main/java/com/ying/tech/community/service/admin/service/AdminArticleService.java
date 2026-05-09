package com.ying.tech.community.service.admin.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.admin.vo.AdminArticleDetailVO;
import com.ying.tech.community.service.admin.vo.AdminArticleListItemVO;

public interface AdminArticleService {
    PageResult<AdminArticleListItemVO> getArticles(String keyword,
                                                   Integer status,
                                                   Long categoryId,
                                                   String authorName,
                                                   Integer page,
                                                   Integer size);

    AdminArticleDetailVO getArticleDetail(Long articleId);

    void deleteArticle(Long articleId);
}
