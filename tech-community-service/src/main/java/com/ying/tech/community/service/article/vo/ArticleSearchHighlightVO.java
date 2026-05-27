package com.ying.tech.community.service.article.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章搜索高亮结果 VO。
 * <p>
 * 高亮显示改为前端实现，后端仅返回纯文本。highlightedTitle / highlightedContent 已废弃，
 * 保留字段为兼容旧版前端，新版本请使用 title + summary + score 字段。
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
     * 内容摘要（纯文本，未高亮）
     */
    private String summary;

    /**
     * 高亮后的标题（@deprecated ES 旧版，保留兼容）
     */
    @Deprecated
    private String highlightedTitle;

    /**
     * 高亮后的内容片段（@deprecated ES 旧版，保留兼容）
     */
    @Deprecated
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
     * 相关性分数（MySQL FULLTEXT 分值）
     */
    private Double score;

    /**
     * 发布时间（epoch 毫秒）
     */
    private Long publishTime;
}
