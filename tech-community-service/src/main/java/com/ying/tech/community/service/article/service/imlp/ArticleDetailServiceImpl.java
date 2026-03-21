package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private RedisTemplate redisTemplate;

    // 分段锁数组：固定 256 个锁，通过 articleId 取模选锁，彻底避免内存泄漏
    private static final int LOCK_SEGMENT_COUNT = 256;
    private final Lock[] segmentLocks;

    public ArticleDetailServiceImpl() {
        segmentLocks = new Lock[LOCK_SEGMENT_COUNT];
        for (int i = 0; i < LOCK_SEGMENT_COUNT; i++) {
            segmentLocks[i] = new ReentrantLock();
        }
    }

    /**
     * 根据文章id获取文章详情
     *
     * */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        //拼接redis的key
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleId;
        //查redis
        ArticleDetailDO articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
        //存在，返回
        if(articleDetailDO != null){
            // 【防穿透闭环】：判断拿到的是不是我们为了防穿透特意塞入的“空对象”
            if (articleDetailDO.getId() == null) {
                log.warn("触发缓存穿透防御，直接拦截非法 articleId: {}", articleId);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL); // 或者 return null
            }
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
            //每调用一次，浏览次数加1
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        }

        //不存在，查数据库
        //尝试获取锁
        Lock lock = getLock(articleId);
        lock.lock();
        try{
            //先看是否有缓存，在进入后看先前有没有线程重建了缓存
            articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
            if(articleDetailDO != null){
                // 【防穿透闭环】：判断拿到的是不是我们为了防穿透特意塞入的“空对象”
                if (articleDetailDO.getId() == null) {
                    log.warn("触发缓存穿透防御，直接拦截非法 articleId: {}", articleId);
                    throw new BusinessException(StatusEnum.PARAM_ILLEGAL); // 或者 return null
                }
                ArticleDetailVO articleDetailVO = new ArticleDetailVO();
                BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
                //每调用一次，浏览次数加1
                safeIncrementViewCount(articleId);
                return articleDetailVO;
            }
            //第一个线程获取锁成功，则进行数据库查询
            Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
            if(articleDetailId != null){
                articleDetailDO = articleDetailMapper.selectById(articleDetailId);
            }
            if (articleDetailDO == null) {
                log.warn("文章详情不存在，articleId: {}", articleId);
                //缓存空对象，防止缓存穿透
                redisTemplate.opsForValue()
                        .set(articleDetailKey,
                                new ArticleDetailDO(), 5, TimeUnit.MINUTES);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
            //重建缓存，设置过期时间：基础1小时 + 0~10分钟随机波动，防御缓存雪崩
            long baseMinutes = 60; // 1小时
            long randomMinutes = ThreadLocalRandom.current().nextLong(0, 11); // 0~10分钟随机数
            long expireMinutes = baseMinutes + randomMinutes;
            redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO, expireMinutes, TimeUnit.MINUTES);
            //每调用一次，浏览次数加1
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        } finally {
            lock.unlock();
        }
    }


    /**
     * 安全地增加文章阅读量，避免 Redis 中 key 不存在或类型错误
     */
    private void safeIncrementViewCount(Long articleId) {
        // 【核心兜底逻辑】：检查 Redis 中是否有这个阅读量 Key
        String viewCountKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT + articleId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(viewCountKey))) {
            // 1. 如果没有，去 MySQL 查出真实的历史阅读量
            ArticleDO article = articleMapper.selectById(articleId);
            Long dbViewCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;

            // 2. 将真实数据塞入 Redis。
            // 【架构细节】：使用 setIfAbsent 而不是 set。
            // 如果极端情况下有100个人同时在Redis没数据时点进文章，只有一个线程能设置成功，其他线程会被挡住，防止旧数据覆盖。
            // 同时顺手设置 30 天的过期时间，防止冷数据永远占用内存。
            redisTemplate.opsForValue().setIfAbsent(viewCountKey, String.valueOf(dbViewCount), 30, TimeUnit.DAYS);
        }
        try {
            redisTemplate.opsForValue().increment(viewCountKey);
            // 动态续期：每次阅读量增加后刷新过期时间，长过期时间 30 天
            redisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("increment失败，回源DB: {}", viewCountKey, e);

            // 1. 从数据库读取真实值
            QueryWrapper<ArticleDO> articleWrapper = new QueryWrapper<ArticleDO>()
                    .select("view_count")
                    .eq("id", articleId);
            ArticleDO article = articleMapper.selectOne(articleWrapper);
            long dbCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;
            // 2. 回填Redis（必须用 String，保证 Redis INCR 可以正常执行）
            redisTemplate.opsForValue().set(viewCountKey, String.valueOf(dbCount));

            // 3. 再自增
            redisTemplate.opsForValue().increment(viewCountKey);

            // 4. 设置过期时间
            redisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        }
    }

    /**
     * 获取锁对象（分段锁，固定内存占用）
     */
    private Lock getLock(Long articleId) {
        int index = (int)(articleId % LOCK_SEGMENT_COUNT);
        if (index < 0) index += LOCK_SEGMENT_COUNT;
        return segmentLocks[index];
    }
}
