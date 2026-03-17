package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;
import com.ying.tech.community.core.constants.RedisConstants;
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

@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 根据文章id获取文章详情
     *
     * */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        //先把文章id转为文章详情表的id
        Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
        if(articleDetailId == null){
            log.warn("文章不存在，articleId: {}", articleId);
            return null;
        }
        //每调用一次，浏览次数加1
        safeIncrementViewCount(articleId);
        //拼接redis的key
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleDetailId;
        ArticleDetailDO articleDetailDO = new ArticleDetailDO();
        //查redis
        articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
        //存在，返回
        if(articleDetailDO != null){
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
            return articleDetailVO;
        }

        //不存在，查数据库
        articleDetailDO = articleDetailMapper.selectById(articleDetailId);
        if (articleDetailDO == null) {
            log.warn("文章详情不存在，articleId: {}", articleId);
            return null;
        }
        ArticleDetailVO articleDetailVO = new ArticleDetailVO();
        BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
        //重建缓存，设置过期时间：基础1小时 + 0~10分钟随机波动，防御缓存雪崩
        long baseMinutes = 60; // 1小时
        long randomMinutes = ThreadLocalRandom.current().nextLong(0, 11); // 0~10分钟随机数
        long expireMinutes = baseMinutes + randomMinutes;
        redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO, expireMinutes, TimeUnit.MINUTES);
        return articleDetailVO;
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
            log.warn("阅读量 increment 失败，key: {}, 错误：{}", viewCountKey, e.getMessage());
            redisTemplate.delete(viewCountKey);
            redisTemplate.opsForValue().set(viewCountKey, 0L);
            redisTemplate.opsForValue().increment(viewCountKey);
            // 异常恢复后也需要设置过期时间
            redisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        }
    }
}
