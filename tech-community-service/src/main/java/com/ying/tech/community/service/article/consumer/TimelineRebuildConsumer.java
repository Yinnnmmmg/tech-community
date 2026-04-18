package com.ying.tech.community.service.article.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.message.TimelineRebuildMessage;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rebuilds article timeline ZSet in Redis when cache is missing.
 */
@Slf4j
@Component
public class TimelineRebuildConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ArticleMapper articleMapper;

    @RabbitListener(queues = "timeline.rebuild.queue", containerFactory = "autoAckListenerContainerFactory")
    public void handleTimelineRebuild(TimelineRebuildMessage message) {
        log.info("[TimelineRebuild] receive rebuild request, rebuildTime={}", message.getRebuildTime());

        try {
            String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;

            // Only approved articles are allowed to enter the public timeline.
            List<ArticleDO> articles = articleMapper.selectList(
                    new QueryWrapper<ArticleDO>()
                            .eq("status", PublishStatusConstants.APPROVED)
                            .orderByDesc("create_time")
                            .last("LIMIT 5000"));

            if (!articles.isEmpty()) {
                redisTemplate.delete(articleListKey);

                Set<ZSetOperations.TypedTuple<Object>> tuples = new HashSet<>(articles.size());
                for (ArticleDO article : articles) {
                    long score = article.getCreateTime()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>(
                            article.getId().toString(), (double) score));
                }
                redisTemplate.opsForZSet().add(articleListKey, tuples);
                log.info("[TimelineRebuild] rebuild finished, total={}", articles.size());
            } else {
                log.warn("[TimelineRebuild] no approved article found, skip rebuild");
            }

        } catch (Exception e) {
            log.error("[TimelineRebuild] rebuild failed, error={}", e.getMessage(), e);
            // Auto ACK mode: this message will not be retried here.
        }
    }
}
