package com.ying.tech.community.service.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.service.ai.service.ArticleEmbeddingService;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章向量化服务实现。
 * 核心职责是把文章标题和正文整理成文档后切分为多个片段，再写入向量库。
 */
@Slf4j
@Service
public class ArticleEmbeddingServiceImpl implements ArticleEmbeddingService {
    private final ArticleMapper articleMapper;
    private final ArticleDetailMapper articleDetailMapper;
    private final VectorStore vectorStore;

    /**
     * 使用 Spring AI 提供的分词切片器，避免长文本一次性入库导致向量粒度过粗。
     */
    private final TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();

    public ArticleEmbeddingServiceImpl(ArticleMapper articleMapper,
                                       ArticleDetailMapper articleDetailMapper,
                                       VectorStore vectorStore) {
        this.articleMapper = articleMapper;
        this.articleDetailMapper = articleDetailMapper;
        this.vectorStore = vectorStore;
    }

    @Override
    public void rebuildArticleEmbedding(Long articleId) {
        // 先校验文章主体是否存在，避免后续使用无效主键构建向量数据。
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new IllegalStateException("article not found, articleId=" + articleId);
        }

        // 只取最新版本正文，保证向量库中的内容与当前展示版本一致。
        ArticleDetailDO detail = articleDetailMapper.selectOne(new QueryWrapper<ArticleDetailDO>()
                .select("content", "version")
                .eq("article_id", articleId)
                .orderByDesc("version")
                .last("LIMIT 1"));

        // 重建前先清掉旧向量，避免历史片段残留导致检索命中脏数据。
        deleteArticleEmbedding(articleId);
        if (detail == null) {
            log.warn("[ArticleEmbedding] detail not found, skip rebuild, articleId={}", articleId);
            return;
        }

        String title = article.getTitle();
        String content = detail.getContent();
        // 标题和正文都为空时没有可索引内容，直接跳过即可。
        if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
            log.warn("[ArticleEmbedding] title and content are blank, skip rebuild, articleId={}", articleId);
            return;
        }

        // 标题和正文共同组成原始语料，元数据用于检索结果过滤和回溯来源。
        String rawText = (title == null ? "" : title) + "\n\n" + (content == null ? "" : content);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("articleId", articleId);
        metadata.put("title", title);
        metadata.put("authorId", article.getUserId());

        Document document = new Document(rawText, metadata);

        // 切片后再写入向量库，便于长文分段召回并降低单条向量的信息噪声。
        List<Document> chunks = tokenTextSplitter.apply(List.of(document));
        vectorStore.add(chunks);
        log.info("[ArticleEmbedding] rebuild success, articleId={}, chunkCount={}", articleId, chunks.size());
    }

    @Override
    public void deleteArticleEmbedding(Long articleId) {
        // 删除操作天然要支持幂等，空 ID 直接返回即可。
        if (articleId == null) {
            return;
        }

        // 通过 metadata 中的 articleId 条件删除，保证只影响当前文章的向量片段。
        vectorStore.delete(new FilterExpressionBuilder().eq("articleId", articleId).build());
        log.info("[ArticleEmbedding] delete success, articleId={}", articleId);
    }
}
