package com.ying.tech.community.service.admin.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminCommentListItemVO implements Serializable {
    private Long commentId;
    private Long articleId;
    private String articleTitle;
    private Long userId;
    private String username;
    private String content;
    private Integer status;
    private String rejectReason;
    private Long parentCommentId;
    private Long replyToCommentId;
    private Long replyToUserId;
    private String replyToUsername;
    private Long likeCount;
    private Long replyCount;
    private String createTime;
    private String updateTime;
}
