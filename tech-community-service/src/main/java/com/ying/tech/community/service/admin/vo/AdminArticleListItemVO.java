package com.ying.tech.community.service.admin.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminArticleListItemVO implements Serializable {
    private Long articleId;
    private Long authorId;
    private String authorName;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private Integer status;
    private String coverUrl;
    private Long likeCount;
    private Long collectionCount;
    private Long commentCount;
    private String createTime;
    private String updateTime;
}
