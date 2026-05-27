package com.ying.tech.community.service.notifyMsg.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知消息视图对象。
 */
@Data
@Builder
public class NotifyMsgVO {
    /**
     * 消息内容。
     */
    private String msg;

    /**
     * 消息创建时间。
     */
    private LocalDateTime createTime;
}
