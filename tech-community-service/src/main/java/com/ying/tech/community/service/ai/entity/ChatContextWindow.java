package com.ying.tech.community.service.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 是在调用模型前组装上下文时用
 * Redis 里取最近消息
 * MySQL 里取会话摘要
 * 当前用户再发来一个问题
 * 可以统一封装成一个上下文对象再去做 query rewrite 和 RAG。
 * */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatContextWindow {
    private String sessionId;
    private Long userId;
    private String title;
    private LocalDateTime createdTime;
    private LocalDateTime lastActiveTime;
    private List<ChatMessageItem> messages = new ArrayList<>();

}
