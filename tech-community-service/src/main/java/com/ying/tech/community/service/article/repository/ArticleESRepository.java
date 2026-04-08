package com.ying.tech.community.service.article.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.ying.tech.community.service.article.entity.es.ArticleDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class ArticleESRepository {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "articles";

    /**
     * 确保索引存在（仅用于开发环境或初始化检查）。
     * 生产环境应预先通过 Dev Tools 创建索引。
     */
    public void ensureIndexExists() {
        try {
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
            boolean exists = elasticsearchClient.indices().exists(existsRequest).value();

            if (!exists) {
                log.warn("ES index '{}' does not exist. Please create it via Dev Tools.", INDEX_NAME);
                // 开发环境可以选择抛出异常或仅记录警告
                // throw new IllegalStateException("ES index not found: " + INDEX_NAME);
            }
        } catch (IOException e) {
            log.error("check index existence failed", e);
            throw new RuntimeException("check index existence failed", e);
        }
    }

    /**
     * 索引一篇文章文档。
     * 使用 refresh=wait_for，确保写入后在后续搜索中可见。
     */
    public void index(ArticleDocument document) {
        try {
            // 以业务主键作为 ES 文档 ID，保证同一文章可覆盖更新。
            IndexRequest<ArticleDocument> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(document.getId().toString())
                    .document(document)
                    .refresh(Refresh.WaitFor)
            );
            // 提交写入请求。
            elasticsearchClient.index(request);
            log.info("document indexed, id={}", document.getId());
        } catch (IOException e) {
            log.error("index document failed, id={}", document.getId(), e);
            throw new RuntimeException("index document failed", e);
        }
    }

    /**
     * 执行带高亮的文章搜索。
     *
     * 高亮规则：
     * 1. title 返回完整高亮内容（numberOfFragments=0）。
     * 2. content 返回一个片段（fragmentSize=180，numberOfFragments=1）。
     * 3. 返回结果包含原始文档、标题高亮和内容高亮。
     */
    public HighlightSearchResult searchWithHighlight(Query query, Integer from, Integer size) {
        try {
            // 构建查询 + 分页 + 高亮策略请求。
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(query)
                    .from(from)
                    .size(size)
                    .highlight(h -> h
                            .preTags("<em>")
                            .postTags("</em>")
                            .fields("title", f -> f.numberOfFragments(0))
                            .fields("content", f -> f.fragmentSize(180).numberOfFragments(1))
                    )
            );

            SearchResponse<ArticleDocument> response = elasticsearchClient.search(searchRequest, ArticleDocument.class);
            // 组装返回记录：每条包含原文档和对应高亮片段。
            List<HighlightArticleRecord> records = new ArrayList<>();

            for (Hit<ArticleDocument> hit : response.hits().hits()) {
                ArticleDocument doc = ArticleDocument.fromHit(hit);
                if (doc == null) {
                    continue;
                }

                Map<String, List<String>> highlightMap = hit.highlight();
                records.add(new HighlightArticleRecord(
                    doc,
                    firstHighlight(highlightMap, "title"),
                    firstHighlight(highlightMap, "content")
                ));
            }


            // ES 可能返回 total 为空（如某些配置场景），兜底用当前 records 数量。
            long total = response.hits().total() == null ? records.size() : response.hits().total().value();
            return new HighlightSearchResult(total, records);
        } catch (IOException e) {
            log.error("search with highlight failed", e);
            throw new RuntimeException("search with highlight failed", e);
        }
    }

    /**
     * 从高亮字段中获取指定字段的第一个高亮片段。
     */
    private String firstHighlight(Map<String, List<String>> highlightMap, String field) {
        // 无高亮结果时返回 null，由上层决定展示原文还是空值。
        if (highlightMap == null || highlightMap.isEmpty()) {
            return null;
        }
        List<String> fragments = highlightMap.getOrDefault(field, Collections.emptyList());
        if (fragments.isEmpty()) {
            return null;
        }
        return fragments.get(0);
    }


    //两个内部类，其他地方不需要使用， 这样可以强关联性和内聚性
    @Getter
    @AllArgsConstructor
    public static class HighlightSearchResult {
        private final long total;
        private final List<HighlightArticleRecord> records;

    }

    @Getter
    @AllArgsConstructor
    public static class HighlightArticleRecord {
        private final ArticleDocument articleDocument;
        private final String highlightedTitle;
        private final String highlightedContent;

    }
}

