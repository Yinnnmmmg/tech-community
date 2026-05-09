package com.ying.tech.community.service.admin.vo;

import com.ying.tech.community.service.article.vo.ArticleAttachmentVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AdminArticleDetailVO implements Serializable {
    private Long articleId;
    private Long authorId;
    private String authorName;
    private String title;
    private String content;
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
    private List<ArticleAttachmentVO> attachments;
}
