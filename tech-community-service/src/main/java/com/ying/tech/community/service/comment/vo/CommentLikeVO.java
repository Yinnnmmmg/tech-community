package com.ying.tech.community.service.comment.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommentLikeVO {
    private Long likeCount;
    private Long likeStat;
}
