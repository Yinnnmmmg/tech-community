package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleListVO implements Serializable {
    private Long articleId;
    private Long authorId;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String authorName;
    private String createTime;
    private String coverUrl;
    private Long likeCount;
    private Long collectionCount;
    private Long commentCount;
    private Long attachmentCount;
    private Boolean hasAttachment;
}
