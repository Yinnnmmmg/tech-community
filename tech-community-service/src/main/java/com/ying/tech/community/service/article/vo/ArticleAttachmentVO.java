package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章附件展示对象。
 * 返回给前端用于展示附件基本信息和访问地址。
 */
@Data
public class ArticleAttachmentVO implements Serializable {
    /**
     * 附件 ID。
     */
    private Long attachmentId;

    /**
     * 展示用文件名。
     */
    private String fileName;

    /**
     * 文件访问地址。
     */
    private String url;

    /**
     * MIME 类型。
     */
    private String contentType;

    /**
     * 文件大小，单位字节。
     */
    private Long fileSize;
}
