package com.ying.tech.community.service.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ying.tech.community.service.admin.service.AdminArticleService;
import com.ying.tech.community.service.admin.service.AdminCommentService;
import com.ying.tech.community.service.admin.service.AdminDashboardService;
import com.ying.tech.community.service.admin.vo.AdminDashboardSummaryVO;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private static final int PENDING_STATUS = 0;
    private static final int ADMIN_ROLE_CODE = 1;

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final AdminArticleService adminArticleService;
    private final AdminCommentService adminCommentService;

    public AdminDashboardServiceImpl(ArticleMapper articleMapper,
                                     CommentMapper commentMapper,
                                     UserMapper userMapper,
                                     AdminArticleService adminArticleService,
                                     AdminCommentService adminCommentService) {
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.adminArticleService = adminArticleService;
        this.adminCommentService = adminCommentService;
    }

    @Override
    public AdminDashboardSummaryVO getSummary() {
        AdminDashboardSummaryVO summary = new AdminDashboardSummaryVO();
        summary.setArticleCount(defaultCount(articleMapper.selectCount(new LambdaQueryWrapper<ArticleDO>())));
        summary.setPendingArticleCount(defaultCount(articleMapper.selectCount(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getStatus, PENDING_STATUS))));
        summary.setCommentCount(defaultCount(commentMapper.selectCount(new LambdaQueryWrapper<CommentDO>())));
        summary.setPendingCommentCount(defaultCount(commentMapper.selectCount(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getStatus, PENDING_STATUS))));
        summary.setUserCount(defaultCount(userMapper.selectCount(new LambdaQueryWrapper<UserDO>())));
        summary.setAdminCount(defaultCount(userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUserRole, ADMIN_ROLE_CODE))));
        summary.setRecentArticles(adminArticleService.getArticles(null, null, null, null, 1, 5).getRecords());
        summary.setRecentComments(adminCommentService.getComments(null, null, null, 1, 5).getRecords());
        return summary;
    }

    private Long defaultCount(Long count) {
        return count == null ? 0L : count;
    }
}
