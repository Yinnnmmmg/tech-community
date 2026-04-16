package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章列表视图对象。
 */
@Data
public class ArticleListVO implements Serializable {
    /** 文章 ID。 */
    private Long articleId;
    /** 文章标题。 */
    private String title;
    /** 文章摘要。 */
    private String summary;
    /** 作者昵称。 */
    private String authorName;
    /** 发布时间。 */
    private String createTime;
    /** 封面图地址。 */
    private String coverUrl;
    /** 附件数量。 */
    private Long attachmentCount;
    /** 是否存在附件。 */
    private Boolean hasAttachment;
}
