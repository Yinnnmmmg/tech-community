package com.ying.tech.community.service.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.admin.req.AdminCategorySaveReq;
import com.ying.tech.community.service.admin.service.AdminCategoryService;
import com.ying.tech.community.service.admin.vo.AdminCategoryVO;
import com.ying.tech.community.service.article.entity.ArticleCategoryDO;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleCategoryMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminCategoryServiceImpl implements AdminCategoryService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleMapper articleMapper;

    public AdminCategoryServiceImpl(ArticleCategoryMapper articleCategoryMapper, ArticleMapper articleMapper) {
        this.articleCategoryMapper = articleCategoryMapper;
        this.articleMapper = articleMapper;
    }

    @Override
    public List<AdminCategoryVO> getCategories() {
        List<ArticleCategoryDO> categories = articleCategoryMapper.selectList(new LambdaQueryWrapper<ArticleCategoryDO>()
                .orderByAsc(ArticleCategoryDO::getSort)
                .orderByAsc(ArticleCategoryDO::getId));
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Long> articleCountMap = articleMapper.selectList(new LambdaQueryWrapper<ArticleDO>()
                        .select(ArticleDO::getId, ArticleDO::getCategoryId))
                .stream()
                .filter(article -> article.getCategoryId() != null)
                .collect(Collectors.groupingBy(ArticleDO::getCategoryId, Collectors.counting()));

        return categories.stream()
                .map(category -> toCategoryVO(category, articleCountMap))
                .toList();
    }

    @Override
    public AdminCategoryVO createCategory(AdminCategorySaveReq req) {
        ArticleCategoryDO category = new ArticleCategoryDO();
        category.setName(req.getName().trim());
        category.setSort(req.getSort());
        category.setStatus(req.getStatus());
        articleCategoryMapper.insert(category);
        return toCategoryVO(articleCategoryMapper.selectById(category.getId()), Collections.emptyMap());
    }

    @Override
    public AdminCategoryVO updateCategory(Long categoryId, AdminCategorySaveReq req) {
        ArticleCategoryDO category = requireCategory(categoryId);
        category.setName(req.getName().trim());
        category.setSort(req.getSort());
        category.setStatus(req.getStatus());
        articleCategoryMapper.updateById(category);
        return toCategoryVO(articleCategoryMapper.selectById(categoryId), Collections.emptyMap());
    }

    @Override
    public void updateCategoryStatus(Long categoryId, Integer status) {
        requireCategory(categoryId);
        articleCategoryMapper.update(null, new LambdaUpdateWrapper<ArticleCategoryDO>()
                .eq(ArticleCategoryDO::getId, categoryId)
                .set(ArticleCategoryDO::getStatus, status));
    }

    private ArticleCategoryDO requireCategory(Long categoryId) {
        ArticleCategoryDO category = articleCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        return category;
    }

    private AdminCategoryVO toCategoryVO(ArticleCategoryDO category, Map<Long, Long> articleCountMap) {
        AdminCategoryVO item = new AdminCategoryVO();
        item.setId(category.getId());
        item.setName(category.getName());
        item.setSort(category.getSort());
        item.setStatus(category.getStatus());
        item.setArticleCount(articleCountMap.getOrDefault(category.getId(), 0L));
        item.setCreateTime(formatTime(category.getCreateTime()));
        item.setUpdateTime(formatTime(category.getUpdateTime()));
        return item;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
