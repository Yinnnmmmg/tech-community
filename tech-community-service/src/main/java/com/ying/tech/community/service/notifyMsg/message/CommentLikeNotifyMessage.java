package com.ying.tech.community.service.notifyMsg.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评论点赞通知消息体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikeNotifyMessage implements Serializable {
    /**
     * 被点赞的评论 ID。
     */
    private Long commentId;

    /**
     * 接收通知的用户 ID，通常是评论作者。
     */
    private Long notifyUserId;

    /**
     * 执行点赞动作的用户 ID。
     */
    private Long operateUserId;
}
