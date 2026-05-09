package com.ying.tech.community.service.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.admin.service.AdminCommentService;
import com.ying.tech.community.service.admin.vo.AdminCommentListItemVO;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.comment.service.CommentReviewService;
import com.ying.tech.community.service.comment.service.CommentService;
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
public class AdminCommentServiceImpl implements AdminCommentService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final CommentReviewService commentReviewService;
    private final CommentService commentService;
    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;

    public AdminCommentServiceImpl(CommentMapper commentMapper,
                                   ArticleMapper articleMapper,
                                   CommentReviewService commentReviewService,
                                   CommentService commentService,
                                   UserMapper userMapper,
                                   UserInfoMapper userInfoMapper) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.commentReviewService = commentReviewService;
        this.commentService = commentService;
        this.userMapper = userMapper;
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public PageResult<AdminCommentListItemVO> getComments(Integer status,
                                                          Long articleId,
                                                          String keyword,
                                                          Integer page,
                                                          Integer size) {
        Page<CommentDO> commentPage = new Page<>(page, size);
        LambdaQueryWrapper<CommentDO> wrapper = new LambdaQueryWrapper<CommentDO>()
                .orderByDesc(CommentDO::getCreateTime)
                .orderByDesc(CommentDO::getId);
        if (status != null) {
            wrapper.eq(CommentDO::getStatus, status);
        }
        if (articleId != null) {
            wrapper.eq(CommentDO::getArticleId, articleId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CommentDO::getContent, keyword.trim());
        }

        Page<CommentDO> resultPage = commentMapper.selectPage(commentPage, wrapper);
        List<CommentDO> records = resultPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageResult<>(resultPage.getTotal(), Collections.emptyList());
        }

        Map<Long, String> articleTitles = loadArticleTitles(records.stream().map(CommentDO::getArticleId).toList());
        List<Long> userIds = records.stream()
                .flatMap(comment -> java.util.stream.Stream.of(comment.getUserId(), comment.getReplyToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> usernames = loadUsernames(userIds);

        List<AdminCommentListItemVO> items = records.stream().map(comment -> {
            AdminCommentListItemVO item = new AdminCommentListItemVO();
            item.setCommentId(comment.getId());
            item.setArticleId(comment.getArticleId());
            item.setArticleTitle(articleTitles.get(comment.getArticleId()));
            item.setUserId(comment.getUserId());
            item.setUsername(usernames.get(comment.getUserId()));
            item.setContent(comment.getContent());
            item.setStatus(comment.getStatus());
            item.setRejectReason(comment.getRejectReason());
            item.setParentCommentId(comment.getParentCommentId());
            item.setReplyToCommentId(comment.getReplyToCommentId());
            item.setReplyToUserId(comment.getReplyToUserId());
            item.setReplyToUsername(usernames.get(comment.getReplyToUserId()));
            item.setLikeCount(longValue(comment.getLikeCount()));
            item.setReplyCount(longValue(comment.getReplyCount()));
            item.setCreateTime(formatTime(comment.getCreateTime()));
            item.setUpdateTime(formatTime(comment.getUpdateTime()));
            return item;
        }).toList();

        return new PageResult<>(resultPage.getTotal(), items);
    }

    @Override
    public void approveComment(Long commentId) {
        CommentDO comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        commentReviewService.approveComment(commentId, comment.getUserId());
    }

    @Override
    public void rejectComment(Long commentId, String reason) {
        commentReviewService.rejectComment(commentId, reason);
    }

    @Override
    public void deleteComment(Long commentId) {
        commentService.deleteCommentByAdmin(commentId);
    }

    private Map<Long, String> loadArticleTitles(List<Long> articleIds) {
        List<Long> uniqueIds = articleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleMapper.selectBatchIds(uniqueIds)
                .stream()
                .collect(Collectors.toMap(ArticleDO::getId, ArticleDO::getTitle, (left, right) -> left));
    }

    private Map<Long, String> loadUsernames(List<Long> userIds) {
        List<Long> uniqueIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserDO> users = userMapper.selectBatchIds(uniqueIds).stream()
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
