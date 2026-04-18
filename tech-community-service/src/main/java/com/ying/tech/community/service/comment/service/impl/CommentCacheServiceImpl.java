package com.ying.tech.community.service.comment.service.impl;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.comment.service.CommentCacheService;
import com.ying.tech.community.service.comment.vo.CommentListItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class CommentCacheServiceImpl implements CommentCacheService {
    private static final long PAGE_CACHE_TTL_SECONDS = 5 * 60L;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取文章维度的公开评论分页缓存。
     */
    @Override
    public PageResult<CommentListItemVO> getArticlePublicPage(Long articleId, Integer page, Integer size,
                                                              Supplier<PageResult<CommentListItemVO>> loader) {
        Long version = getVersion(RedisConstants.TECH_COMMUNITY_COMMENT_ARTICLE_LIST_VERSION + articleId);
        String cacheKey = RedisConstants.TECH_COMMUNITY_COMMENT_ARTICLE_LIST_PAGE
                + articleId + ":v:" + version + ":p:" + page + ":s:" + size;
        return getOrLoadPage(cacheKey, loader);
    }

    /**
     * 获取顶层评论下公开回复分页缓存。
     */
    @Override
    public PageResult<CommentListItemVO> getReplyPublicPage(Long parentCommentId, Integer page, Integer size,
                                                            Supplier<PageResult<CommentListItemVO>> loader) {
        Long version = getVersion(RedisConstants.TECH_COMMUNITY_COMMENT_REPLY_LIST_VERSION + parentCommentId);
        String cacheKey = RedisConstants.TECH_COMMUNITY_COMMENT_REPLY_LIST_PAGE
                + parentCommentId + ":v:" + version + ":p:" + page + ":s:" + size;
        return getOrLoadPage(cacheKey, loader);
    }

    /**
     * 文章评论列表失效时推进版本号，避免逐页扫描删除缓存。
     */
    @Override
    public void bumpArticleListVersion(Long articleId) {
        redisTemplate.opsForValue().increment(RedisConstants.TECH_COMMUNITY_COMMENT_ARTICLE_LIST_VERSION + articleId);
    }

    /**
     * 顶层评论回复列表失效时推进版本号。
     */
    @Override
    public void bumpReplyListVersion(Long parentCommentId) {
        redisTemplate.opsForValue().increment(RedisConstants.TECH_COMMUNITY_COMMENT_REPLY_LIST_VERSION + parentCommentId);
    }

    /**
     * 清理文章摘要缓存，避免文章列表里的 commentCount 长时间不一致。
     */
    @Override
    public void clearArticleSummaryCache(Long articleId) {
        redisTemplate.delete(RedisConstants.TECH_COMMUNITY_ARTICLE + articleId);
    }

    /**
     * 清理评论点赞集合缓存，避免删除评论后留下孤儿 key。
     */
    @Override
    public void clearCommentLikeCache(Collection<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }
        redisTemplate.delete(commentIds.stream()
                .map(commentId -> RedisConstants.TECH_COMMUNITY_COMMENT_LIKE + commentId)
                .toList());
    }

    /**
     * 读取分页缓存，未命中时回源数据库并回填。
     */
    @SuppressWarnings("unchecked")
    private PageResult<CommentListItemVO> getOrLoadPage(String cacheKey, Supplier<PageResult<CommentListItemVO>> loader) {
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue instanceof PageResult<?>) {
            return (PageResult<CommentListItemVO>) cachedValue;
        }

        PageResult<CommentListItemVO> loadedPage = loader.get();
        if (loadedPage == null) {
            loadedPage = new PageResult<>(0L, Collections.emptyList());
        }
        redisTemplate.opsForValue().set(cacheKey, loadedPage, buildExpireSeconds(), TimeUnit.SECONDS);
        return loadedPage;
    }

    /**
     * 获取当前版本号，未初始化时默认从 0 开始。
     */
    private Long getVersion(String versionKey) {
        Object versionValue = redisTemplate.opsForValue().get(versionKey);
        if (versionValue instanceof Number number) {
            return number.longValue();
        }
        if (versionValue instanceof String versionStr) {
            try {
                return Long.parseLong(versionStr);
            } catch (NumberFormatException ignore) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * 给分页缓存加一点随机抖动，降低同一时刻大面积过期的概率。
     */
    private long buildExpireSeconds() {
        return PAGE_CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, 61);
    }
}
