package com.ying.tech.community.service.comment.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class CommentListItemVO implements Serializable {
    private Long commentId;
    private Long articleId;
    private Long userId;
    private String username;
    private String content;
    private Integer status;
    private String rejectReason;
    private Long likeCount;
    private Long likeStat;
    private Long replyCount;
    private Long replyToUserId;
    private String replyToUsername;
    private String createTime;
    private Boolean canDelete;
}
