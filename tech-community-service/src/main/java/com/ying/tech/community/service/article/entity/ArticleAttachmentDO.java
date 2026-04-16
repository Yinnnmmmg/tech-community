package com.ying.tech.community.service.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

/**
 * 文章附件实体。
 * 记录附件在数据库中的元数据，以及它与文章之间的绑定关系。
 */
@Data
@TableName("article_attachment")
public class ArticleAttachmentDO extends BaseDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 附件主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 绑定的文章 ID，未绑定时为空。
     */
    private Long articleId;

    /**
     * 上传该附件的用户 ID。
     */
    private Long userId;

    /**
     * 用户上传时的原始文件名。
     */
    private String originFileName;

    /**
     * 文件在对象存储中的唯一键。
     */
    private String objectKey;

    /**
     * 对外访问地址。
     */
    private String url;

    /**
     * MIME 类型，例如 image/png。
     */
    private String contentType;

    /**
     * 文件大小，单位为字节。
     */
    private Long fileSize;

    /**
     * 文件扩展名，统一保存为小写。
     */
    private String fileExt;

    /**
     * 附件业务状态，见 {@code ArticleAttachmentStatusConstants}。
     */
    private Integer status;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;
}
