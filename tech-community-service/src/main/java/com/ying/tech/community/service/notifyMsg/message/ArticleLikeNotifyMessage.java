package com.ying.tech.community.service.notifyMsg.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章点赞通知消息体。
 * 用于把点赞行为转换为通知落库所需的上下文信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLikeNotifyMessage implements Serializable {
    /**
     * 被点赞的文章 ID。
     */
    private Long articleId;

    /**
     * 接收通知的用户 ID，通常是文章作者。
     */
    private Long notifyUserId;

    /**
     * 执行点赞动作的用户 ID。
     */
    private Long operateUserId;
}
