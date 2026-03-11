package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleListVO implements Serializable {
    private Long articleId;
    private String title;
    private String summary;
    private String authorName;
    private String createTime;
}