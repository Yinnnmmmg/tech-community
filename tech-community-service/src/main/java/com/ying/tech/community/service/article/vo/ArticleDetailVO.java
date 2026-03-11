package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章详情 VO
 * 用于返回文章详情数据给前端
 */
@Data
public class ArticleDetailVO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * 文章主键 ID
     */
    private Long articleId;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章完整正文 (Markdown 格式)
     */
    private String content;

    /**
     * 作者昵称
     */
    private String authorName;

    /**
     * 所属分类名称 (前端可直接展示的中文名)
     */
    private String categoryName;

    /**
     * 发布时间
     */
    private String createTime;
}
