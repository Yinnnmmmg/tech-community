package com.ying.tech.community.service.admin.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AdminDashboardSummaryVO implements Serializable {
    private Long articleCount;
    private Long pendingArticleCount;
    private Long commentCount;
    private Long pendingCommentCount;
    private Long userCount;
    private Long adminCount;
    private List<AdminArticleListItemVO> recentArticles;
    private List<AdminCommentListItemVO> recentComments;
}
