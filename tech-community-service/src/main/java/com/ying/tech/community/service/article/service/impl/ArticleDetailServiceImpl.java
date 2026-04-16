package com.ying.tech.community.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.ArticleStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleAttachmentService;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 文章详情查询服务实现。
 *
 * <p>负责文章详情缓存、浏览量累加以及当前用户点赞/收藏状态回填。
 */
@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    /** 固定分段锁数量，用于防止详情缓存击穿。 */
    private static final int LOCK_SEGMENT_COUNT = 256;
    /** 文章时间展示格式。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final ArticleDetailMapper articleDetailMapper;
    private final ArticleAttachmentService articleAttachmentService;
    private final UserMapper userMapper;
    private final UserFootMapper userFootMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    /** 分段锁数组，按 articleId 取模定位，避免锁对象无限增长。 */
    private final Lock[] segmentLocks;

    public ArticleDetailServiceImpl(ArticleMapper articleMapper,
                                    ArticleDetailMapper articleDetailMapper,
                                    ArticleAttachmentService articleAttachmentService,
                                    UserMapper userMapper,
                                    UserFootMapper userFootMapper,
                                    RedisTemplate<String, Object> redisTemplate,
                                    StringRedisTemplate stringRedisTemplate) {
        this.articleMapper = articleMapper;
        this.articleDetailMapper = articleDetailMapper;
        this.articleAttachmentService = articleAttachmentService;
        this.userMapper = userMapper;
        this.userFootMapper = userFootMapper;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.segmentLocks = new Lock[LOCK_SEGMENT_COUNT];
        for (int i = 0; i < LOCK_SEGMENT_COUNT; i++) {
            this.segmentLocks[i] = new ReentrantLock();
        }
    }

    /**
     * 根据文章 ID 查询详情。
     *
     * <p>优先从 Redis 读取详情缓存，未命中时使用分段锁回源数据库，并在成功读取后递增浏览量。
     */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        ArticleDO article = getApprovedArticle(articleId);
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleId;
        ArticleDetailDO articleDetailDO = (ArticleDetailDO) redisTemplate.opsForValue().get(articleDetailKey);
        if (articleDetailDO != null) {
            if (articleDetailDO.getId() == null) {
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            safeIncrementViewCount(articleId);
            return buildArticleDetailVO(article, articleDetailDO);
        }

        Lock lock = getLock(articleId);
        lock.lock();
        try {
            articleDetailDO = (ArticleDetailDO) redisTemplate.opsForValue().get(articleDetailKey);
            if (articleDetailDO != null) {
                if (articleDetailDO.getId() == null) {
                    throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
                }
                safeIncrementViewCount(articleId);
                return buildArticleDetailVO(article, articleDetailDO);
            }

            Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
            if (articleDetailId != null) {
                articleDetailDO = articleDetailMapper.selectById(articleDetailId);
            }
            if (articleDetailDO == null) {
                redisTemplate.opsForValue().set(articleDetailKey, new ArticleDetailDO(), 5, TimeUnit.MINUTES);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }

            long expireMinutes = 60 + ThreadLocalRandom.current().nextLong(0, 11);
            redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO, expireMinutes, TimeUnit.MINUTES);
            safeIncrementViewCount(articleId);
            return buildArticleDetailVO(article, articleDetailDO);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验文章是否存在且已审核通过。
     */
    private ArticleDO getApprovedArticle(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null || !java.util.Objects.equals(article.getStatus(), ArticleStatusConstants.APPROVED)) {
            log.warn("article not approved, articleId={}", articleId);
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        return article;
    }

    /**
     * 组装文章详情返回对象。
     */
    private ArticleDetailVO buildArticleDetailVO(ArticleDO article, ArticleDetailDO articleDetailDO) {
        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setArticleId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setContent(articleDetailDO.getContent());
        vo.setAuthorName(loadAuthorName(article.getUserId()));
        vo.setCreateTime(formatTime(article.getCreateTime()));
        vo.setCoverUrl(article.getPicture());
        vo.setLikeCount(article.getLikeCount() == null ? 0L : article.getLikeCount().longValue());
        vo.setCollectionCount(article.getCollectionCount() == null ? 0L : article.getCollectionCount().longValue());
        Long currentUserId = ReqInfoContext.getReqInfo() == null ? null : ReqInfoContext.getReqInfo().getUserId();
        vo.setLikeStat(resolveLikeStat(article.getId(), currentUserId));
        vo.setCollectionStat(resolveCollectionStat(article.getId(), currentUserId));
        vo.setAttachments(articleAttachmentService.listBoundAttachments(article.getId()));
        return vo;
    }

    /**
     * 查询作者名称。
     */
    private String loadAuthorName(Long userId) {
        if (userId == null) {
            return null;
        }
        UserDO user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    /**
     * 格式化时间字段。
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    /**
     * 安全递增浏览量。
     *
     * <p>优先在 Redis 中累加，Redis 初始化或累加失败时回退数据库值再继续。
     */
    private void safeIncrementViewCount(Long articleId) {
        String viewCountKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT + articleId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(viewCountKey))) {
            ArticleDO article = articleMapper.selectById(articleId);
            Long dbViewCount = (article != null && article.getViewCount() != null) ? article.getViewCount().longValue() : 0L;
            stringRedisTemplate.opsForValue().setIfAbsent(viewCountKey, String.valueOf(dbViewCount), 30, TimeUnit.DAYS);
        }
        try {
            stringRedisTemplate.opsForValue().increment(viewCountKey);
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            QueryWrapper<ArticleDO> articleWrapper = new QueryWrapper<ArticleDO>()
                    .select("view_count")
                    .eq("id", articleId);
            ArticleDO article = articleMapper.selectOne(articleWrapper);
            long dbCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;
            stringRedisTemplate.opsForValue().set(viewCountKey, String.valueOf(dbCount));
            stringRedisTemplate.opsForValue().increment(viewCountKey);
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        }
    }

    /**
     * 根据文章 ID 映射到固定分段锁。
     */
    private Lock getLock(Long articleId) {
        int index = (int) (articleId % LOCK_SEGMENT_COUNT);
        if (index < 0) {
            index += LOCK_SEGMENT_COUNT;
        }
        return segmentLocks[index];
    }

    /**
     * 解析当前用户对文章的点赞状态。
     */
    private Long resolveLikeStat(Long articleId, Long userId) {
        return resolveUserActionStat(
                RedisConstants.TECH_COMMUNITY_ARTICLE_LIKE + articleId,
                articleId,
                userId,
                true
        );
    }

    /**
     * 解析当前用户对文章的收藏状态。
     */
    private Long resolveCollectionStat(Long articleId, Long userId) {
        return resolveUserActionStat(
                RedisConstants.TECH_COMMUNITY_ARTICLE_COLLECT + articleId,
                articleId,
                userId,
                false
        );
    }

    /**
     * 从 Redis 或数据库解析用户互动状态。
     */
    private Long resolveUserActionStat(String redisKey, Long articleId, Long userId, boolean likeAction) {
        if (userId == null) {
            return 0L;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            Boolean member = redisTemplate.opsForSet().isMember(redisKey, userId);
            return Boolean.TRUE.equals(member) ? 1L : 0L;
        }

        UserFootDO userFootDO = userFootMapper.selectOne(new QueryWrapper<UserFootDO>()
                .eq("user_id", userId)
                .eq("document_id", articleId)
                .last("limit 1"));
        if (userFootDO == null) {
            return 0L;
        }
        Integer stat = likeAction ? userFootDO.getLikeStat() : userFootDO.getCollectionStat();
        return stat != null && stat == 1 ? 1L : 0L;
    }
}
