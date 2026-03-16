package com.ying.tech.community.service.article.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArticleLikeVO {
    private Long likeCount; // 点赞数
    private Long likeStat;  // 点赞状态 0-未点赞 1-已点赞
}
