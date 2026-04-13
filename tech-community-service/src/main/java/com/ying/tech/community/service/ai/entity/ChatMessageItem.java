package com.ying.tech.community.service.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessageItem implements Serializable {
    /** 消息唯一 id*/
    private String messageId;
    /** 所属会话 id*/
    private String sessionId;
    /*** 消息角色*/
    private ChatRole role;
    /*** 消息内容*/
    private String content;
    /*** 创建时间*/
    private LocalDateTime createdTime;
    /*** 本条回答引用的文章 id，可选*/
    private List<Long> referenceArticleIds;
}
