package com.ying.tech.community.service.article.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文章收藏操作返回对象。
 * 同时返回当前总收藏数和当前用户的收藏状态，便于前端直接刷新按钮状态。
 */
@Data
@Builder
public class ArticleCollectVO {
    /**
     * 当前文章收藏总数。
     */
    private Long collectionCount;

    /**
     * 当前用户收藏状态，1 表示已收藏，0 表示未收藏。
     */
    private Long collectionStat;
}
