package com.ying.tech.community.service.comment.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommentPublishReq implements Serializable {
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentCommentId;

    private Long replyToCommentId;

    private Long replyToUserId;
}