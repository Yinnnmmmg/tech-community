package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ArticleDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long articleId;
    private String title;
    private String content;
    private String authorName;
    private String categoryName;
    private String createTime;
    private String coverUrl;
    private Long likeCount;
    private Long collectionCount;
    private Long commentCount;
    private Long likeStat;
    private Long collectionStat;
    private List<ArticleAttachmentVO> attachments;
}
