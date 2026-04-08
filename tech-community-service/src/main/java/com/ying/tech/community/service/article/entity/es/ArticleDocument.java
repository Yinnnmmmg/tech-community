package com.ying.tech.community.service.article.entity.es;

import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.Data;

import java.util.List;

@Data
public class ArticleDocument {
    private Long id;
    private String title;
    private String content;
    private String author;
    private Long authorId;
    private List<String> tags;
    private Integer readCount;
    private Integer likeCount;
    private Long publishTime;
    private Long createTime;
    private Long updateTime;
    /** 相关性得分（ES _score，类型为 Double）。 */
    private Double score;

    /**
     * 封装元数据转换成实体类的逻辑，代码复用，修改转换逻辑时不用修改业务代码。
     * 从 Elasticsearch 查询命中结果中构造 ArticleDocument。
     * 将 hit.source() 转换成 ArticleDocument。
     * @param hit ES 命中结果
     * @return 转换后的文档；当 hit 为空时返回 null
     */
    public static ArticleDocument fromHit(Hit<ArticleDocument> hit) {
        // 极端情况下 source 为空，直接跳过。
        if (hit == null) {
            return null;
        }
        ArticleDocument document = hit.source();
        // 把相关性得分回填到文档对象，便于上层使用。
        if (document != null && hit.score() != null) {
            document.setScore(hit.score());
        }
        return document;
    }
}
