package com.ying.tech.community.service.article.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArticleLikeVO {
    private Integer likeCount;
    private Integer likeStat;
}
