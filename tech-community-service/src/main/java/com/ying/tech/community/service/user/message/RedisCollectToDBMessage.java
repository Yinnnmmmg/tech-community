package com.ying.tech.community.service.user.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 收藏行为落库消息。
 * 用于把 Redis 中的收藏状态异步同步到数据库和聚合计数。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RedisCollectToDBMessage implements Serializable {

    /**
     * 执行收藏动作的用户 ID。
     */
    private Long userId;

    /**
     * 被收藏的文档 ID，这里实际承载文章 ID。
     */
    private Long documentId;

    /**
     * 文档作者 ID，用于后续生成通知消息。
     */
    private Long documentUserId;

    /**
     * 阅读状态，兼容用户足迹表的读状态字段。
     */
    private Integer readStat;

    /**
     * 收藏状态，1 表示收藏，0 表示取消收藏。
     */
    private Integer collectionStat;
}
