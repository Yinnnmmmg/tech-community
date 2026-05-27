package com.ying.tech.community.service.article.service.impl;

// [ES-OLD] import co.elastic.clients.elasticsearch._types.FieldValue;
// [ES-OLD] import co.elastic.clients.elasticsearch._types.query_dsl.*;
// [ES-OLD] import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// [ES-OLD] import com.ying.tech.community.service.article.entity.es.ArticleDocument;
// [ES-OLD] import com.ying.tech.community.service.article.repository.ArticleESRepository;
import com.ying.tech.community.service.article.repository.mapper.ArticleSearchMapper;
import com.ying.tech.community.service.article.service.ArticleSearchService;
import com.ying.tech.community.service.article.vo.ArticleSearchHighlightVO;
import com.ying.tech.community.service.article.vo.ArticleSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ArticleSearchServiceImpl implements ArticleSearchService {

    // [ES-OLD] @Autowired
    // [ES-OLD] private ArticleESRepository articleESRepository;

    @Autowired
    private ArticleSearchMapper articleSearchMapper;


    /**
     * 全文搜索（基于 MySQL FULLTEXT 索引）。
     * <p>
     * 高亮显示由前端实现，后端仅返回纯文本标题和摘要。
     */
    @Override
    public Page<ArticleSearchHighlightVO> searchWithHighlight(String keyword, Integer page, Integer size) {
        // 分页参数兜底，避免出现非法页码或页大小
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : size;
        Page<ArticleSearchHighlightVO> pageResult = new Page<>(safePage, safeSize);

        // 关键词为空时直接返回空结果，避免无意义查询
        if (!StringUtils.hasText(keyword)) {
            pageResult.setTotal(0L);
            pageResult.setRecords(Collections.emptyList());
            return pageResult;
        }

        // === MySQL FULLTEXT 搜索 ===
        long total = articleSearchMapper.countByKeyword(keyword);
        int offset = (safePage - 1) * safeSize;
        List<ArticleSearchResultVO> searchResults =
                articleSearchMapper.searchByKeyword(keyword, offset, safeSize);

        // 将 MySQL 查询结果映射为接口返回 VO
        List<ArticleSearchHighlightVO> records = searchResults.stream().map(item ->
                ArticleSearchHighlightVO.builder()
                        .id(item.getId())
                        .title(item.getTitle())
                        .summary(item.getSummary())
                        .author(item.getAuthor())
                        .authorId(item.getAuthorId())
                        .score(item.getRelevance())
                        .publishTime(toEpochMillis(item.getCreateTime()))
                        .build()
        ).toList();

        pageResult.setTotal(total);
        pageResult.setRecords(records);
        return pageResult;


        // ===================================================================
        // [ES-OLD] 以下为旧的 Elasticsearch 搜索实现，保留备用
        // ===================================================================
        /*
        // 构造 ES 查询：title 提高权重，同时匹配 content/tags
        Query query = BoolQuery.of(b -> b
                .should(s -> s.multiMatch(m -> m
                        .fields("title^3", "content", "tags")
                        .query(keyword)
                        .type(TextQueryType.BestFields)
                ))
                .minimumShouldMatch("1")
        )._toQuery();
        // 将页码转换为 ES from 偏移量
        int from = (safePage - 1) * safeSize;

        // 执行带高亮的查询
        ArticleESRepository.HighlightSearchResult searchResult =
                articleESRepository.searchWithHighlight(query, from, safeSize);

        // 将仓储层高亮结果映射为接口返回 VO；无高亮时回退原文/摘要
        List<ArticleSearchHighlightVO> records = searchResult.getRecords().stream().map(item -> {
            ArticleDocument document = item.getArticleDocument();
            return ArticleSearchHighlightVO.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .highlightedTitle(StringUtils.hasText(item.getHighlightedTitle())
                            ? item.getHighlightedTitle() : document.getTitle())
                    .highlightedContent(StringUtils.hasText(item.getHighlightedContent())
                            ? item.getHighlightedContent() : briefContent(document.getContent()))
                    .author(document.getAuthor())
                    .authorId(document.getAuthorId())
                    .tags(document.getTags())
                    .score(document.getScore())
                    .publishTime(document.getPublishTime())
                    .build();
        }).toList();

        // 组装分页返回结果
        pageResult.setTotal(searchResult.getTotal());
        pageResult.setRecords(records);
        return pageResult;
        */
    }

    // ===================================================================
    // [ES-OLD] 以下两个方法为 ES 旧版使用的辅助方法，保留备用
    // ===================================================================
    /*
    private String briefContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        int maxLen = 180;
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }
    */

    /**
     * 将数据库时间字符串转为 epoch 毫秒。
     */
    private Long toEpochMillis(String timeStr) {
        if (!StringUtils.hasText(timeStr)) {
            return null;
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timeStr.replace("T", " "));
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.warn("parse createTime failed, timeStr={}", timeStr, e);
            return null;
        }
    }
}
