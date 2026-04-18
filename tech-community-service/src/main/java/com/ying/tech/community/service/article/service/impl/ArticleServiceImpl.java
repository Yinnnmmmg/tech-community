package com.ying.tech.community.service.article.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ying.tech.community.core.common.CursorPageResult;
import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.ai.service.ArticleEmbeddingService;
import com.ying.tech.community.service.article.entity.ArticleAttachmentDO;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.article.message.TimelineRebuildMessage;
import com.ying.tech.community.service.article.repository.ArticleESRepository;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.req.ArticleUpdateReq;
import com.ying.tech.community.service.article.service.ArticleAttachmentService;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleCollectVO;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.message.RedisCollectToDBMessage;
import com.ying.tech.community.service.user.message.RedisLikeToDBMessage;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 文章服务实现。
 *
 * <p>负责文章发布、编辑、删除、列表查询以及点赞/收藏等读写操作，
 * 并在事务提交后触发时间轴、搜索和知识库等异步链路。
 */
@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {
    /** 统一的时间格式化器。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DOCUMENT_TYPE_ARTICLE = 1;

    private final ArticleMapper articleMapper;
    private final ArticleDetailMapper articleDetailMapper;
    private final UserFootMapper userFootMapper;
    private final UserMapper userMapper;
    private final ArticleAttachmentService articleAttachmentService;
    private final ArticleEmbeddingService articleEmbeddingService;
    private final ArticleESRepository articleESRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final Executor taskExecutor;

    public ArticleServiceImpl(ArticleMapper articleMapper,
                              ArticleDetailMapper articleDetailMapper,
                              UserFootMapper userFootMapper,
                              UserMapper userMapper,
                              ArticleAttachmentService articleAttachmentService,
                              ArticleEmbeddingService articleEmbeddingService,
                              ArticleESRepository articleESRepository,
                              RedisTemplate<String, Object> redisTemplate,
                              RabbitTemplate rabbitTemplate,
                              Executor taskExecutor) {
        this.articleMapper = articleMapper;
        this.articleDetailMapper = articleDetailMapper;
        this.userFootMapper = userFootMapper;
        this.userMapper = userMapper;
        this.articleAttachmentService = articleAttachmentService;
        this.articleEmbeddingService = articleEmbeddingService;
        this.articleESRepository = articleESRepository;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 发布文章。
     *
     * <p>写入文章主表、详情表并绑定附件，事务提交后再异步发送审核消息。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long publishArticle(ArticlePostReq articlePostReq) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        log.info("publish article, userId={}", userId);

        ArticleDO article = new ArticleDO();
        BeanUtil.copyProperties(articlePostReq, article);
        article.setUserId(userId);
        article.setStatus(PublishStatusConstants.PENDING);
        articleMapper.insert(article);

        ArticleDetailDO articleDetail = new ArticleDetailDO();
        articleDetail.setArticleId(article.getId());
        articleDetail.setContent(articlePostReq.getContent());
        articleDetailMapper.insert(articleDetail);

        List<ArticleAttachmentDO> boundAttachments = articleAttachmentService.bindAttachmentsToArticle(
                article.getId(),
                userId,
                articlePostReq.getAttachmentIds()
        );
        String coverUrl = resolveCoverUrl(boundAttachments);
        if (StringUtils.hasText(coverUrl)) {
            article.setPicture(coverUrl);
            articleMapper.updateById(article);
        }

        long currentTime = System.currentTimeMillis();
        ArticlePublishMessage message = new ArticlePublishMessage(article.getId(), userId, currentTime);
        registerAfterCommit(() -> sendPublishMessage(article.getId(), message));

        return article.getId();
    }

    /**
     * 更新文章内容并重新提交审核。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long updateArticle(Long articleId, ArticleUpdateReq articleUpdateReq) {
        ArticleDO article = requireOwnedArticle(articleId);
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        log.info("update article, articleId={}, userId={}", articleId, currentUserId);

        List<ArticleAttachmentDO> boundAttachments = articleAttachmentService.replaceAttachmentsOnArticle(
                articleId,
                currentUserId,
                articleUpdateReq.getAttachmentIds()
        );
        articleMapper.update(null, new UpdateWrapper<ArticleDO>()
                .eq("id", articleId)
                .set("title", articleUpdateReq.getTitle())
                .set("category_id", articleUpdateReq.getCategoryId())
                .set("summary", null)
                .set("picture", resolveCoverUrl(boundAttachments))
                .set("status", PublishStatusConstants.PENDING));

        ArticleDetailDO detail = articleDetailMapper.selectOne(new QueryWrapper<ArticleDetailDO>()
                .eq("article_id", articleId)
                .last("LIMIT 1"));
        if (detail == null) {
            detail = new ArticleDetailDO();
            detail.setArticleId(articleId);
            detail.setContent(articleUpdateReq.getContent());
            detail.setVersion(1);
            articleDetailMapper.insert(detail);
        } else {
            detail.setContent(articleUpdateReq.getContent());
            detail.setVersion(detail.getVersion() == null ? 1 : detail.getVersion() + 1);
            articleDetailMapper.updateById(detail);
        }

        Long publishTime = article.getCreateTime() == null
                ? System.currentTimeMillis()
                : article.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ArticlePublishMessage message = new ArticlePublishMessage(articleId, currentUserId, publishTime);
        registerAfterCommit(() -> {
            clearArticleReadSide(articleId);
            sendPublishMessage(articleId, message);
        });
        return articleId;
    }

    /**
     * 删除当前用户自己的文章。
     *
     * <p>删除完成后会在事务提交后清理缓存、时间轴、搜索索引和知识库数据。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteArticle(Long articleId) {
        ArticleDO article = requireOwnedArticle(articleId);
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        log.info("delete article, articleId={}, userId={}", articleId, currentUserId);

        articleAttachmentService.releaseAttachmentsOnArticle(articleId);
        articleDetailMapper.delete(new QueryWrapper<ArticleDetailDO>().eq("article_id", articleId));
        articleMapper.deleteById(article.getId());

        registerAfterCommit(() -> clearArticleReadSide(articleId));
    }

    /**
     * 基于 Redis 时间轴游标分页查询文章列表。
     *
     * <p>优先从 Redis ZSet 读取文章 ID，再批量回填文章缓存，最后补充作者和附件信息。
     * 若时间轴缓存不存在或为空，则退化为数据库分页查询。
     */
    @Override
    public CursorPageResult<ArticleListVO> getArticleList(Long cursor, Integer pageSize) {
        log.info("query article list, cursor={}, pageSize={}", cursor, pageSize);

        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        long maxScore = (cursor == null || cursor <= 0) ? System.currentTimeMillis() : cursor - 1;
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                articleListKey,
                0,
                maxScore,
                0,
                pageSize
        );

        if (typedTuples == null || typedTuples.isEmpty()) {
            Boolean hasKey = redisTemplate.hasKey(articleListKey);
            if (Boolean.FALSE.equals(hasKey) || hasKey == null) {
                rabbitTemplate.convertAndSend("article.direct", "timeline.rebuild", new TimelineRebuildMessage(System.currentTimeMillis()));
            }
            return queryArticleListFromDB(cursor, pageSize);
        }

        List<String> orderedIds = new ArrayList<>();
        Long nextCursor = null;
        for (ZSetOperations.TypedTuple<Object> typedTuple : typedTuples) {
            orderedIds.add((String) typedTuple.getValue());
            nextCursor = typedTuple.getScore().longValue();
        }

        List<String> keys = orderedIds.stream()
                .map(id -> RedisConstants.TECH_COMMUNITY_ARTICLE + id)
                .collect(Collectors.toList());
        List<Object> cachedArticles = redisTemplate.opsForValue().multiGet(keys);
        if (cachedArticles == null) {
            cachedArticles = new ArrayList<>(Collections.nCopies(keys.size(), null));
        }

        List<String> missingIds = new ArrayList<>();
        for (int i = 0; i < cachedArticles.size(); i++) {
            if (cachedArticles.get(i) == null) {
                missingIds.add(orderedIds.get(i));
            }
        }

        Map<String, ArticleDO> missingArticlesMap = new HashMap<>();
        if (!missingIds.isEmpty()) {
            List<ArticleDO> dbArticles = articleMapper.selectList(new QueryWrapper<ArticleDO>()
                    .in("id", missingIds)
                    .eq("status", PublishStatusConstants.APPROVED));

            Map<String, Object> redisBatchData = new HashMap<>();
            for (ArticleDO article : dbArticles) {
                String idStr = article.getId().toString();
                missingArticlesMap.put(idStr, article);
                redisBatchData.put(RedisConstants.TECH_COMMUNITY_ARTICLE + idStr, article);
            }

            if (!redisBatchData.isEmpty()) {
                redisTemplate.opsForValue().multiSet(redisBatchData);
                for (String key : redisBatchData.keySet()) {
                    redisTemplate.expire(key,
                            8 * 60 + ThreadLocalRandom.current().nextLong(0, 61),
                            TimeUnit.SECONDS);
                }
            }
        }

        List<ArticleDO> finalArticles = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            String articleId = orderedIds.get(i);
            ArticleDO articleDO = cachedArticles.get(i) != null
                    ? (ArticleDO) cachedArticles.get(i)
                    : missingArticlesMap.get(articleId);
            if (articleDO != null && Objects.equals(articleDO.getStatus(), PublishStatusConstants.APPROVED)) {
                finalArticles.add(articleDO);
            }
        }

        if (orderedIds.size() < pageSize) {
            nextCursor = null;
        }

        return new CursorPageResult<>(nextCursor, enrichArticleList(finalArticles));
    }

    /**
     * 数据库游标分页兜底查询。
     */
    private CursorPageResult<ArticleListVO> queryArticleListFromDB(Long cursor, Integer pageSize) {
        QueryWrapper<ArticleDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", PublishStatusConstants.APPROVED)
                .orderByDesc("create_time")
                .last("LIMIT " + pageSize);

        if (cursor != null && cursor > 0) {
            LocalDateTime cursorTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZoneId.systemDefault());
            queryWrapper.lt("create_time", cursorTime);
        }

        List<ArticleDO> articleDOList = articleMapper.selectList(queryWrapper);
        List<ArticleListVO> voList = enrichArticleList(articleDOList);

        Long nextCursor = null;
        if (!articleDOList.isEmpty()) {
            LocalDateTime createTime = articleDOList.get(articleDOList.size() - 1).getCreateTime();
            if (createTime != null) {
                nextCursor = createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        }
        if (articleDOList.size() < pageSize) {
            nextCursor = null;
        }
        return new CursorPageResult<>(nextCursor, voList);
    }

    /**
     * 切换文章点赞状态，并通过 MQ 异步落库。
     */
    @Override
    public ArticleLikeVO likeArticle(Long articleId) {
        ArticleDO article = requireApprovedArticle(articleId);
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        String likeKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIKE + articleId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(likeKey))) {
            List<UserFootDO> userFootDOList = userFootMapper.selectList(
                    new QueryWrapper<UserFootDO>()
                            .eq("document_id", articleId)
                            .eq("document_type", DOCUMENT_TYPE_ARTICLE)
                            .eq("like_stat", 1)
            );
            List<Long> likedUserIds = userFootDOList.stream()
                    .map(UserFootDO::getUserId)
                    .collect(Collectors.toList());
            if (!likedUserIds.isEmpty()) {
                redisTemplate.opsForSet().add(likeKey, likedUserIds.toArray());
                redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);
            }
        }

        Long addResult = redisTemplate.opsForSet().add(likeKey, currentUserId);
        Long likeStat = Long.valueOf(1L).equals(addResult) ? 1L : 0L;
        if (Long.valueOf(0L).equals(likeStat)) {
            redisTemplate.opsForSet().remove(likeKey, currentUserId);
        }
        redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);

        RedisLikeToDBMessage redisLikeToDBMessage = RedisLikeToDBMessage.builder()
                .userId(currentUserId)
                .documentId(articleId)
                .documentUserId(article.getUserId())
                .readStat(1)
                .likeStat(likeStat.intValue())
                .build();
        taskExecutor.execute(() -> {
            try {
                String message = UUID.randomUUID().toString();
                CorrelationData correlationData = new CorrelationData(message);
                rabbitTemplate.convertAndSend("article.direct", "article.like", redisLikeToDBMessage,
                        msg -> {
                            msg.getMessageProperties().setCorrelationId(message);
                            msg.getMessageProperties().setMessageId(message);
                            return msg;
                        }, correlationData);
            } catch (Exception e) {
                log.error("send like message failed, articleId={}, userId={}", articleId, currentUserId, e);
            }
        });

        return ArticleLikeVO.builder()
                .likeCount(redisTemplate.opsForSet().size(likeKey))
                .likeStat(likeStat)
                .build();
    }

    /**
     * 切换文章收藏状态，并通过 MQ 异步落库。
     */
    @Override
    public ArticleCollectVO collectArticle(Long articleId) {
        ArticleDO article = requireApprovedArticle(articleId);
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        String collectKey = RedisConstants.TECH_COMMUNITY_ARTICLE_COLLECT + articleId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(collectKey))) {
            List<UserFootDO> userFootDOList = userFootMapper.selectList(
                    new QueryWrapper<UserFootDO>()
                            .eq("document_id", articleId)
                            .eq("document_type", DOCUMENT_TYPE_ARTICLE)
                            .eq("collection_stat", 1)
            );
            List<Long> collectedUserIds = userFootDOList.stream()
                    .map(UserFootDO::getUserId)
                    .collect(Collectors.toList());
            if (!collectedUserIds.isEmpty()) {
                redisTemplate.opsForSet().add(collectKey, collectedUserIds.toArray());
                redisTemplate.expire(collectKey, 30, TimeUnit.DAYS);
            }
        }

        Long addResult = redisTemplate.opsForSet().add(collectKey, currentUserId);
        Long collectionStat = Long.valueOf(1L).equals(addResult) ? 1L : 0L;
        if (Long.valueOf(0L).equals(collectionStat)) {
            redisTemplate.opsForSet().remove(collectKey, currentUserId);
        }
        redisTemplate.expire(collectKey, 30, TimeUnit.DAYS);

        RedisCollectToDBMessage redisCollectToDBMessage = RedisCollectToDBMessage.builder()
                .userId(currentUserId)
                .documentId(articleId)
                .documentUserId(article.getUserId())
                .readStat(1)
                .collectionStat(collectionStat.intValue())
                .build();
        taskExecutor.execute(() -> {
            try {
                String message = UUID.randomUUID().toString();
                CorrelationData correlationData = new CorrelationData(message);
                rabbitTemplate.convertAndSend("article.direct", "article.collect", redisCollectToDBMessage,
                        msg -> {
                            msg.getMessageProperties().setCorrelationId(message);
                            msg.getMessageProperties().setMessageId(message);
                            return msg;
                        }, correlationData);
            } catch (Exception e) {
                log.error("send collect message failed, articleId={}, userId={}", articleId, currentUserId, e);
            }
        });

        return ArticleCollectVO.builder()
                .collectionCount(redisTemplate.opsForSet().size(collectKey))
                .collectionStat(collectionStat)
                .build();
    }

    /**
     * 发送文章审核消息。
     */
    private void sendPublishMessage(Long articleId, ArticlePublishMessage message) {
        try {
            String messageId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend("article.direct", "article.publish.review", message, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                return msg;
            }, correlationData);
            log.info("publish review message sent, articleId={}, messageId={}", articleId, messageId);
        } catch (Exception e) {
            log.error("send publish review message failed, articleId={}", articleId, e);
        }
    }

    /**
     * 为文章列表补充作者名、封面和附件统计信息。
     */
    private List<ArticleListVO> enrichArticleList(List<ArticleDO> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return Collections.emptyList();
        }

        List<Long> articleIds = articles.stream().map(ArticleDO::getId).collect(Collectors.toList());
        Map<Long, Long> attachmentCountMap = articleAttachmentService.countBoundAttachments(articleIds);
        Map<Long, String> authorNameMap = loadAuthorNameMap(articles.stream()
                .map(ArticleDO::getUserId)
                .collect(Collectors.toList()));

        List<ArticleListVO> result = new ArrayList<>(articles.size());
        for (ArticleDO article : articles) {
            long attachmentCount = attachmentCountMap.getOrDefault(article.getId(), 0L);
            ArticleListVO vo = new ArticleListVO();
            vo.setArticleId(article.getId());
            vo.setTitle(article.getTitle());
            vo.setSummary(article.getSummary());
            vo.setAuthorName(authorNameMap.get(article.getUserId()));
            vo.setCreateTime(formatTime(article.getCreateTime()));
            vo.setCoverUrl(article.getPicture());
            vo.setLikeCount(article.getLikeCount() == null ? 0L : article.getLikeCount().longValue());
            vo.setCollectionCount(article.getCollectionCount() == null ? 0L : article.getCollectionCount().longValue());
            vo.setCommentCount(article.getCommentCount() == null ? 0L : article.getCommentCount().longValue());
            vo.setAttachmentCount(attachmentCount);
            vo.setHasAttachment(attachmentCount > 0);
            result.add(vo);
        }
        return result;
    }

    /**
     * 批量加载作者名称映射。
     */
    private Map<Long, String> loadAuthorNameMap(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<Long> uniqueUserIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(uniqueUserIds)) {
            return Collections.emptyMap();
        }
        List<UserDO> users = userMapper.selectBatchIds(uniqueUserIds);
        Map<Long, String> authorMap = new LinkedHashMap<>();
        for (UserDO user : users) {
            authorMap.put(user.getId(), user.getUsername());
        }
        return authorMap;
    }

    /**
     * 从附件列表中挑选封面图 URL。
     */
    private String resolveCoverUrl(List<ArticleAttachmentDO> attachments) {
        if (CollectionUtils.isEmpty(attachments)) {
            return null;
        }
        for (ArticleAttachmentDO attachment : attachments) {
            if (attachment.getContentType() != null && attachment.getContentType().startsWith("image/")) {
                return attachment.getUrl();
            }
        }
        return null;
    }

    /**
     * 清理文章所有读侧数据。
     */
    private void clearArticleReadSide(Long articleId) {
        try {
            clearArticleCache(articleId);
        } catch (Exception e) {
            log.error("clear article cache failed, articleId={}", articleId, e);
        }
        try {
            removeArticleFromTimeline(articleId);
        } catch (Exception e) {
            log.error("remove article from timeline failed, articleId={}", articleId, e);
        }
        try {
            removeArticleFromSearch(articleId);
        } catch (Exception e) {
            log.error("remove article from search failed, articleId={}", articleId, e);
        }
        try {
            removeArticleFromKnowledgeBase(articleId);
        } catch (Exception e) {
            log.error("remove article from knowledge base failed, articleId={}", articleId, e);
        }
    }

    /**
     * 清理文章缓存。
     */
    private void clearArticleCache(Long articleId) {
        redisTemplate.delete(List.of(
                RedisConstants.TECH_COMMUNITY_ARTICLE + articleId,
                RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleId
        ));
    }

    /**
     * 从文章时间轴中移除文章。
     */
    private void removeArticleFromTimeline(Long articleId) {
        redisTemplate.opsForZSet().remove(RedisConstants.TECH_COMMUNITY_ARTICLE_LIST, articleId.toString());
    }

    /**
     * 从 ES 检索索引中移除文章。
     */
    private void removeArticleFromSearch(Long articleId) {
        articleESRepository.deleteById(articleId);
    }

    /**
     * 从知识库向量存储中移除文章。
     */
    private void removeArticleFromKnowledgeBase(Long articleId) {
        articleEmbeddingService.deleteArticleEmbedding(articleId);
    }

    /**
     * 注册事务提交后的异步回调。
     */
    private void registerAfterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(runnable);
            }
        });
    }

    /**
     * 校验文章存在且归当前登录用户所有。
     */
    private ArticleDO requireOwnedArticle(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        if (!Objects.equals(article.getUserId(), currentUserId)) {
            throw new BusinessException(StatusEnum.ARTICLE_ACCESS_DENIED);
        }
        return article;
    }

    /**
     * 校验文章存在且已审核通过。
     */
    private ArticleDO requireApprovedArticle(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null || !Objects.equals(article.getStatus(), PublishStatusConstants.APPROVED)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        return article;
    }

    /**
     * 格式化时间字段。
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
