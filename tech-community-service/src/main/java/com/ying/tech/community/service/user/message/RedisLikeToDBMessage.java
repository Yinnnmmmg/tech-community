package com.ying.tech.community.service.user.message;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RedisLikeToDBMessage implements Serializable {
    
    /**
     * 用户 ID
     */
    private Long userId;
    
    /**
     * 文档/文章 ID
     */
    private Long documentId;
    
    /**
     * 文档/文章作者 ID
     */
    private Long documentUserId;
    
    /**
     * 阅读状态（0：未读，1：已读）
     */
    private Integer readStat;
    
    /**
     * 点赞状态（0：未点赞，1：已点赞）
     */
    private Integer likeStat;

}
