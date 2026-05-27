package com.ying.tech.community.service.article.vo;

import lombok.Data;

/**
 * 文章搜索中间结果 VO（MyBatis resultMap 映射）。
 * <p>
 * 仅用于搜索服务内部，不直接暴露给 Controller。
 */
@Data
public class ArticleSearchResultVO {

    /** 文章 ID */
    private Long id;

    /** 文章标题 */
    private String title;

    /** 文章摘要（AI 生成或截取） */
    private String summary;

    /** 作者 ID */
    private Long authorId;

    /** 作者名称 */
    private String author;

    /** 创建时间（字符串，由 MyBatis 序列化） */
    private String createTime;

    /** 浏览数 */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 收藏数 */
    private Integer collectionCount;

    /** MySQL FULLTEXT 相关性分值（title 权重 3x + content 权重 1x） */
    private Double relevance;
}
