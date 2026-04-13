package com.ying.tech.community.service.article.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.ArticleStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 分段锁数组：固定 256 把锁，按 articleId 取模映射，避免锁对象无限增长
    private static final int LOCK_SEGMENT_COUNT = 256;
    private final Lock[] segmentLocks;

    public ArticleDetailServiceImpl() {
        segmentLocks = new Lock[LOCK_SEGMENT_COUNT];
        for (int i = 0; i < LOCK_SEGMENT_COUNT; i++) {
            segmentLocks[i] = new ReentrantLock();
        }
    }

    /**
     * 根据文章 id 获取文章详情
     */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        if (!isArticleApproved(articleId)) {
            log.warn("article not approved, articleId: {}", articleId);
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        // Redis 缓存 key
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleId;
        // 先查 Redis
        ArticleDetailDO articleDetailDO = (ArticleDetailDO) redisTemplate.opsForValue().get(articleDetailKey);
        // 缓存命中
        if (articleDetailDO != null) {
            // 空对象占位命中，说明文章详情不存在，直接拦截
            if (articleDetailDO.getId() == null) {
                log.warn("文章详情缓存命中空对象，articleId: {}", articleId);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
            // 浏览量安全自增
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        }

        // 缓存未命中，使用分段锁防止缓存击穿
        Lock lock = getLock(articleId);
        lock.lock();
        try {
            // 双重检查，避免拿到锁前已经有线程回填缓存
            articleDetailDO = (ArticleDetailDO) redisTemplate.opsForValue().get(articleDetailKey);
            if (articleDetailDO != null) {
                // 空对象占位命中，说明文章详情不存在，直接拦截
                if (articleDetailDO.getId() == null) {
                    log.warn("文章详情缓存命中空对象，articleId: {}", articleId);
                    throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
                }
                ArticleDetailVO articleDetailVO = new ArticleDetailVO();
                BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
                // 浏览量安全自增
                safeIncrementViewCount(articleId);
                return articleDetailVO;
            }

            // 查询数据库中的文章详情
            Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
            if (articleDetailId != null) {
                articleDetailDO = articleDetailMapper.selectById(articleDetailId);
            }
            if (articleDetailDO == null) {
                log.warn("文章详情不存在，articleId: {}", articleId);
                // 写入空对象占位，短时间内防止缓存穿透
                redisTemplate.opsForValue().set(articleDetailKey, new ArticleDetailDO(), 5, TimeUnit.MINUTES);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }

            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO, articleDetailVO);

            // 正常数据写入缓存，基础 60 分钟并追加 0~10 分钟随机过期时间，避免雪崩
            long baseMinutes = 60; // 1 小时
            long randomMinutes = ThreadLocalRandom.current().nextLong(0, 11); // 0~10 分钟随机值
            long expireMinutes = baseMinutes + randomMinutes;
            redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO, expireMinutes, TimeUnit.MINUTES);

            // 浏览量安全自增
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 安全地递增浏览量，优先操作 Redis，失败时回退到数据库值再重试
     */
    private void safeIncrementViewCount(Long articleId) {
        // 浏览量缓存 key
        String viewCountKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT + articleId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(viewCountKey))) {
            // Redis 中没有浏览量时，先从 MySQL 读取当前值
            ArticleDO article = articleMapper.selectById(articleId);
            Long dbViewCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;

            // 使用 setIfAbsent 初始化 Redis，避免并发下覆盖已经被其他线程更新的值
            // 这里统一设置 30 天过期时间，后续每次自增时续期
            stringRedisTemplate.opsForValue().setIfAbsent(viewCountKey, String.valueOf(dbViewCount), 30, TimeUnit.DAYS);
        }
        try {
            stringRedisTemplate.opsForValue().increment(viewCountKey);
            // 每次自增后续期，维持 30 天有效期
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("浏览量自增失败，key: {}", viewCountKey, e);

            // 重新从数据库读取浏览量
            QueryWrapper<ArticleDO> articleWrapper = new QueryWrapper<ArticleDO>()
                    .select("view_count")
                    .eq("id", articleId);
            ArticleDO article = articleMapper.selectOne(articleWrapper);
            long dbCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;

            // 回写 Redis 基础值，确保后续 INCR 可继续执行
            stringRedisTemplate.opsForValue().set(viewCountKey, String.valueOf(dbCount));

            // 再次尝试自增
            stringRedisTemplate.opsForValue().increment(viewCountKey);

            // 重新设置过期时间
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        }
    }

    private boolean isArticleApproved(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        return article != null && java.util.Objects.equals(article.getStatus(), ArticleStatusConstants.APPROVED);
    }

    /**
     * 根据文章 id 映射到固定分段锁，避免为每篇文章单独创建锁对象
     */
    private Lock getLock(Long articleId) {
        int index = (int) (articleId % LOCK_SEGMENT_COUNT);
        if (index < 0) {
            index += LOCK_SEGMENT_COUNT;
        }
        return segmentLocks[index];
    }
}
