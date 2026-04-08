package com.ying.tech.community.service.article.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章搜索高亮结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSearchHighlightVO {
    /**
     * 文章 ID
     */
    private Long id;

    /**
     * 文章标题（原始）
     */
    private String title;

    /**
     * 高亮后的标题
     */
    private String highlightedTitle;

    /**
     * 高亮后的内容片段（摘要）
     */
    private String highlightedContent;

    /**
     * 作者名称
     */
    private String author;

    /**
     * 作者 ID
     */
    private Long authorId;

    /**
     * 标签列表
     */
    private java.util.List<String> tags;

    /**
     * 相关性分数
     */
    private Double score;

    /**
     * 发布时间
     */
    private Long publishTime;
}
