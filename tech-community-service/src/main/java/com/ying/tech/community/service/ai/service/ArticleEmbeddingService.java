package com.ying.tech.community.service.ai.service;

/**
 * 文章向量化服务。
 * 负责维护文章内容在向量库中的索引数据，供后续语义检索或知识库能力使用。
 */
public interface ArticleEmbeddingService {
    /**
     * 按文章维度重建向量数据。
     * 一般在文章发布、更新审核通过或全文内容变更后调用。
     *
     * @param articleId 文章 ID
     */
    void rebuildArticleEmbedding(Long articleId);

    /**
     * 删除文章对应的向量数据。
     * 一般在文章被删除、下线或重建前的清理阶段调用。
     *
     * @param articleId 文章 ID
     */
    void deleteArticleEmbedding(Long articleId);
}
