package com.ying.tech.community.service.article.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文章更新请求。
 * 用于承接前端提交的标题、正文、分类和附件绑定信息。
 */
@Data
public class ArticleUpdateReq implements Serializable {
    /**
     * 文章标题。
     */
    @NotBlank(message = "文章标题不能为空")
    private String title;

    /**
     * 文章正文内容。
     */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /**
     * 文章所属分类 ID。
     */
    @NotNull(message = "请选择文章分类")
    private Long categoryId;

    /**
     * 需要绑定到文章上的附件 ID 列表。
     * 传空或不传表示清空当前文章附件。
     */
    private List<Long> attachmentIds;
}
