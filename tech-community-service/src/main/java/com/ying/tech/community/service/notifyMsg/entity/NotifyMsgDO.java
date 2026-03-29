package com.ying.tech.community.service.notifyMsg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("notify_msg")
@Builder
public class NotifyMsgDO extends BaseDO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long relatedId;

    private Long notifyUserId;

    private Long operateUserId;

    private String msg;

    private Integer type;

    private Integer state;
}