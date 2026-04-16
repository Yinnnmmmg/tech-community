package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文章详情视图对象。
 *
 * <p>用于向前端返回文章详情页所需的完整数据。
 */
@Data
public class ArticleDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文章主键 ID。 */
    private Long articleId;
    /** 文章标题。 */
    private String title;
    /** 文章正文内容。 */
    private String content;
    /** 作者昵称。 */
    private String authorName;
    /** 分类名称。 */
    private String categoryName;
    /** 发布时间。 */
    private String createTime;
    /** 封面图地址。 */
    private String coverUrl;
    /** 点赞总数。 */
    private Long likeCount;
    /** 收藏总数。 */
    private Long collectionCount;
    /** 当前用户点赞状态，1 表示已点赞。 */
    private Long likeStat;
    /** 当前用户收藏状态，1 表示已收藏。 */
    private Long collectionStat;
    /** 当前文章绑定的附件列表。 */
    private List<ArticleAttachmentVO> attachments;
}
