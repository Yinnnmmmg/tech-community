package com.ying.tech.community.service.article.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Redis 时间轴重建消息
 * 当检测到 ZSet 缓存丢失时，发送此消息触发消费者从数据库重建时间轴
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimelineRebuildMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 重建时间
     */
    private Long rebuildTime;
}
