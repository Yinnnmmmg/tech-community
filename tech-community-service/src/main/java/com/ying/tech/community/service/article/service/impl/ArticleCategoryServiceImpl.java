package com.ying.tech.community.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.service.article.entity.ArticleCategoryDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleCategoryMapper;
import com.ying.tech.community.service.article.service.ArticleCategoryService;
import com.ying.tech.community.service.article.vo.ArticleCategoryVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ArticleCategoryServiceImpl implements ArticleCategoryService {
    private static final int ENABLED_STATUS = 1;

    private final ArticleCategoryMapper articleCategoryMapper;

    public ArticleCategoryServiceImpl(ArticleCategoryMapper articleCategoryMapper) {
        this.articleCategoryMapper = articleCategoryMapper;
    }

    @Override
    public List<ArticleCategoryVO> listEnabledCategories() {
        List<ArticleCategoryDO> categories = articleCategoryMapper.selectList(new QueryWrapper<ArticleCategoryDO>()
                .eq("status", ENABLED_STATUS)
                .orderByAsc("sort", "id"));
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsEnabledCategory(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        Long count = articleCategoryMapper.selectCount(new QueryWrapper<ArticleCategoryDO>()
                .eq("id", categoryId)
                .eq("status", ENABLED_STATUS));
        return count != null && count > 0;
    }

    @Override
    public String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ArticleCategoryDO category = articleCategoryMapper.selectOne(new QueryWrapper<ArticleCategoryDO>()
                .select("id", "name")
                .eq("id", categoryId)
                .eq("status", ENABLED_STATUS)
                .last("LIMIT 1"));
        return category == null ? null : category.getName();
    }

    private ArticleCategoryVO toVO(ArticleCategoryDO category) {
        ArticleCategoryVO vo = new ArticleCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        return vo;
    }
}
