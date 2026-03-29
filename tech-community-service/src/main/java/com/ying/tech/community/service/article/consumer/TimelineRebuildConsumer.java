package com.ying.tech.community.service.article.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * Redis ZSet 时间轴重建消费者
 *
 * <p>消费队列：timeline.rebuild.queue（绑定到 article.direct，routingKey=timeline.rebuild）
 *
 * <p>触发时机：{@code getArticleList} 检测到 ZSet Key 不存在（缓存丢失）时发送消息
 *
 * <p>重建策略：从 MySQL 查最近 5000 篇文章，按 createTime 毫秒时间戳作为 score 批量写入 ZSet
 *
 * <p>消息策略：Auto ACK + 失败丢弃（非关键业务，允许偶尔失败）
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
        log.info("[TimelineRebuild] 收到重建消息, rebuildTime: {}", message.getRebuildTime());

        try {
            String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;

            // 查最近 5000 篇文章（按发布时间降序），用于重建时间轴
            List<ArticleDO> articles = articleMapper.selectList(
                    new QueryWrapper<ArticleDO>()
                            .orderByDesc("create_time")
                            .last("LIMIT 5000"));

            if (!articles.isEmpty()) {
                // 先删除旧 Key，再批量写入，保证 ZSet 内容与 DB 一致
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
                log.info("[TimelineRebuild] ZSet 重建完成，共写入 {} 条", articles.size());
            } else {
                log.warn("[TimelineRebuild] 数据库中无文章数据，ZSet 重建跳过");
            }

        } catch (Exception e) {
            log.error("[TimelineRebuild] ZSet 重建失败, error: {}, 消息将被丢弃", e.getMessage(), e);
            // Auto ACK 模式下，异常会导致消息被直接丢弃，不重试
        }
    }
}