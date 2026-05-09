package com.ying.tech.community.service.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.admin.service.AdminArticleService;
import com.ying.tech.community.service.admin.vo.AdminArticleDetailVO;
import com.ying.tech.community.service.admin.vo.AdminArticleListItemVO;
import com.ying.tech.community.service.article.entity.ArticleCategoryDO;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleCategoryMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleAttachmentService;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserInfoDO;
import com.ying.tech.community.service.user.repository.mapper.UserInfoMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminArticleServiceImpl implements AdminArticleService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final ArticleDetailMapper articleDetailMapper;
    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleAttachmentService articleAttachmentService;
    private final ArticleService articleService;
    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;

    public AdminArticleServiceImpl(ArticleMapper articleMapper,
                                   ArticleDetailMapper articleDetailMapper,
                                   ArticleCategoryMapper articleCategoryMapper,
                                   ArticleAttachmentService articleAttachmentService,
                                   ArticleService articleService,
                                   UserMapper userMapper,
                                   UserInfoMapper userInfoMapper) {
        this.articleMapper = articleMapper;
        this.articleDetailMapper = articleDetailMapper;
        this.articleCategoryMapper = articleCategoryMapper;
        this.articleAttachmentService = articleAttachmentService;
        this.articleService = articleService;
        this.userMapper = userMapper;
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public PageResult<AdminArticleListItemVO> getArticles(String keyword,
                                                          Integer status,
                                                          Long categoryId,
                                                          String authorName,
                                                          Integer page,
                                                          Integer size) {
        Page<ArticleDO> articlePage = new Page<>(page, size);
        LambdaQueryWrapper<ArticleDO> wrapper = new LambdaQueryWrapper<ArticleDO>()
                .orderByDesc(ArticleDO::getUpdateTime)
                .orderByDesc(ArticleDO::getId);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(ArticleDO::getTitle, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(ArticleDO::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(ArticleDO::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(authorName)) {
            List<Long> authorIds = userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                            .select(UserDO::getId)
                            .like(UserDO::getUsername, authorName.trim()))
                    .stream()
                    .map(UserDO::getId)
                    .toList();
            if (authorIds.isEmpty()) {
                return new PageResult<>(0L, Collections.emptyList());
            }
            wrapper.in(ArticleDO::getUserId, authorIds);
        }

        Page<ArticleDO> resultPage = articleMapper.selectPage(articlePage, wrapper);
        List<ArticleDO> records = resultPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageResult<>(resultPage.getTotal(), Collections.emptyList());
        }

        Map<Long, String> authorNames = loadAuthorNames(records.stream().map(ArticleDO::getUserId).toList());
        Map<Long, String> categoryNames = loadCategoryNames(records.stream().map(ArticleDO::getCategoryId).toList());

        List<AdminArticleListItemVO> items = records.stream()
                .map(article -> toListItem(article, authorNames, categoryNames))
                .toList();
        return new PageResult<>(resultPage.getTotal(), items);
    }

    @Override
    public AdminArticleDetailVO getArticleDetail(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }
        ArticleDetailDO detail = articleDetailMapper.selectOne(new LambdaQueryWrapper<ArticleDetailDO>()
                .eq(ArticleDetailDO::getArticleId, articleId)
                .orderByDesc(ArticleDetailDO::getVersion)
                .orderByDesc(ArticleDetailDO::getId)
                .last("limit 1"));

        AdminArticleDetailVO response = new AdminArticleDetailVO();
        response.setArticleId(article.getId());
        response.setAuthorId(article.getUserId());
        response.setAuthorName(loadAuthorNames(List.of(article.getUserId())).get(article.getUserId()));
        response.setTitle(article.getTitle());
        response.setContent(detail == null ? null : detail.getContent());
        response.setSummary(article.getSummary());
        response.setCategoryId(article.getCategoryId());
        response.setCategoryName(loadCategoryNames(List.of(article.getCategoryId())).get(article.getCategoryId()));
        response.setStatus(article.getStatus());
        response.setCoverUrl(article.getPicture());
        response.setLikeCount(longValue(article.getLikeCount()));
        response.setCollectionCount(longValue(article.getCollectionCount()));
        response.setCommentCount(longValue(article.getCommentCount()));
        response.setCreateTime(formatTime(article.getCreateTime()));
        response.setUpdateTime(formatTime(article.getUpdateTime()));
        response.setAttachments(articleAttachmentService.listBoundAttachments(articleId));
        return response;
    }

    @Override
    public void deleteArticle(Long articleId) {
        articleService.deleteArticleByAdmin(articleId);
    }

    private AdminArticleListItemVO toListItem(ArticleDO article,
                                              Map<Long, String> authorNames,
                                              Map<Long, String> categoryNames) {
        AdminArticleListItemVO item = new AdminArticleListItemVO();
        item.setArticleId(article.getId());
        item.setAuthorId(article.getUserId());
        item.setAuthorName(authorNames.get(article.getUserId()));
        item.setTitle(article.getTitle());
        item.setSummary(article.getSummary());
        item.setCategoryId(article.getCategoryId());
        item.setCategoryName(categoryNames.get(article.getCategoryId()));
        item.setStatus(article.getStatus());
        item.setCoverUrl(article.getPicture());
        item.setLikeCount(longValue(article.getLikeCount()));
        item.setCollectionCount(longValue(article.getCollectionCount()));
        item.setCommentCount(longValue(article.getCommentCount()));
        item.setCreateTime(formatTime(article.getCreateTime()));
        item.setUpdateTime(formatTime(article.getUpdateTime()));
        return item;
    }

    private Map<Long, String> loadCategoryNames(List<Long> categoryIds) {
        List<Long> uniqueIds = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleCategoryMapper.selectList(new LambdaQueryWrapper<ArticleCategoryDO>()
                        .in(ArticleCategoryDO::getId, uniqueIds))
                .stream()
                .collect(Collectors.toMap(ArticleCategoryDO::getId, ArticleCategoryDO::getName, (left, right) -> left));
    }

    private Map<Long, String> loadAuthorNames(List<Long> userIds) {
        List<Long> uniqueIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserDO> users = userMapper.selectBatchIds(uniqueIds)
                .stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
        Map<Long, UserInfoDO> infos = userInfoMapper.selectList(new LambdaQueryWrapper<UserInfoDO>()
                        .in(UserInfoDO::getUserId, uniqueIds))
                .stream()
                .collect(Collectors.toMap(UserInfoDO::getUserId, Function.identity(), (left, right) -> left));

        return uniqueIds.stream().collect(Collectors.toMap(
                Function.identity(),
                userId -> {
                    UserInfoDO info = infos.get(userId);
                    if (info != null && StringUtils.hasText(info.getUsername())) {
                        return info.getUsername();
                    }
                    UserDO user = users.get(userId);
                    return user == null ? "user-" + userId : user.getUsername();
                }
        ));
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private Long longValue(Integer value) {
        return value == null ? 0L : value.longValue();
    }
}
