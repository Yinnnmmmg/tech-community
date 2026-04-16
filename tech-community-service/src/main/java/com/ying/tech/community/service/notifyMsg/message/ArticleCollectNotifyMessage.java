package com.ying.tech.community.service.notifyMsg.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章收藏通知消息体。
 * 用于在收藏业务和通知业务之间传递最小必要字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCollectNotifyMessage implements Serializable {
    /**
     * 被收藏的文章 ID。
     */
    private Long articleId;

    /**
     * 接收通知的用户 ID，通常是文章作者。
     */
    private Long notifyUserId;

    /**
     * 触发收藏操作的用户 ID。
     */
    private Long operateUserId;
}
