package com.ying.tech.community.service.article.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.service.article.entity.es.ArticleDocument;
import com.ying.tech.community.service.article.repository.ArticleESRepository;
import com.ying.tech.community.service.article.service.ArticleSearchService;
import com.ying.tech.community.service.article.vo.ArticleSearchHighlightVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ArticleSearchServiceImpl implements ArticleSearchService {

    @Autowired
    private ArticleESRepository articleESRepository;


    /**
     * 全文高亮搜索。
     * <p>
     * 关键点：
     * 1. 对 page/size 做最小值兜底；
     * 2. 关键词为空时直接返回空分页；
     * 3. 优先使用 ES 高亮片段，未命中高亮时回退原文摘要。
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
                    .highlightedTitle(StringUtils.hasText(item.getHighlightedTitle()) ? item.getHighlightedTitle() : document.getTitle())
                    .highlightedContent(StringUtils.hasText(item.getHighlightedContent()) ? item.getHighlightedContent() : briefContent(document.getContent()))
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
    }

    /**
     * 内容摘要截断：超长内容截取前 180 个字符。
     */
    private String briefContent(String content) {
        // 空内容直接返回空串，避免 NPE
        if (!StringUtils.hasText(content)) {
            return "";
        }

        int maxLen = 180;
        // 未超长直接返回原文
        if (content.length() <= maxLen) {
            return content;
        }

        // 超长时做摘要截断
        return content.substring(0, maxLen) + "...";
    }
}