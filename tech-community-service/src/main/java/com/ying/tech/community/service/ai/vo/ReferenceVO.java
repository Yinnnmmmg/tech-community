package com.ying.tech.community.service.ai.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReferenceVO {
    /**
     * 命中的本地知识库文章 ID，供前端渲染超链接点击跳转
     */
    private Long articleId;

    /**
     * 文章标题
     */
    private String title;
}