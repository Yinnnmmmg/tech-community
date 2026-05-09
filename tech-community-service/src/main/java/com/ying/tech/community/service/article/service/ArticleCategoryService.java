package com.ying.tech.community.service.article.service;

import com.ying.tech.community.service.article.vo.ArticleCategoryVO;

import java.util.List;

public interface ArticleCategoryService {
    List<ArticleCategoryVO> listEnabledCategories();

    boolean existsEnabledCategory(Long categoryId);

    String getCategoryName(Long categoryId);
}
