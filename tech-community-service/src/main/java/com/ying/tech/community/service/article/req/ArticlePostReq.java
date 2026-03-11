package com.ying.tech.community.service.article.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ArticlePostReq implements Serializable {
    @NotBlank(message = "文章标题不能为空")
    private String title;
    @NotBlank(message = "文章内容不能为空")
    private String content;
    @NotNull(message = "请选择文章分类")
    private Long categoryId;
}
