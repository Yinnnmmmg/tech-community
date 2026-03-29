package com.ying.tech.community.service.article.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticlePublishMessage implements Serializable {
    private Long articleId;
    private Long authorId; // 把作者ID传过去
    /**
     * 新增：发文的准确时间戳，供 Redis ZSet 排行榜使用
     */
    private Long publishTime;
}
