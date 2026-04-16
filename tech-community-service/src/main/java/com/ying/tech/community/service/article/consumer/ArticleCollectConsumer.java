package com.ying.tech.community.service.article.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.notifyMsg.message.ArticleCollectNotifyMessage;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.message.RedisCollectToDBMessage;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文章收藏落库消费者。
 * 负责把 Redis 中的收藏状态同步到关系库，同时维护文章收藏数并触发站内通知。
 */
@Component
@Slf4j
public class ArticleCollectConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.collect:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;
    private static final long IDEMPOTENT_FALLBACK_TTL_MINUTES = 5;
    private static final int DOCUMENT_TYPE_ARTICLE = 1;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserFootMapper userFootMapper;
    private final ArticleMapper articleMapper;
    private final RabbitTemplate rabbitTemplate;

    public ArticleCollectConsumer(RedisTemplate<String, Object> redisTemplate,
                                  UserFootMapper userFootMapper,
                                  ArticleMapper articleMapper,
                                  RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.userFootMapper = userFootMapper;
        this.articleMapper = articleMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "article.collect.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handleArticleCollect(RedisCollectToDBMessage message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId)
            throws IOException {
        // 消息里同时携带操作者、文章和文章作者信息，便于一次消费完成全流程处理。
        log.info("[ArticleCollect] receive, userId={}, documentId={}, documentUserId={}, readStat={}, collectionStat={}",
                message.getUserId(), message.getDocumentId(), message.getDocumentUserId(), message.getReadStat(), message.getCollectionStat());

        String idempotentKey;
        long idempotentTtl;
        TimeUnit idempotentTtlUnit;
        // 优先使用 MQ messageId 做幂等；如果消息头缺失，则退化为业务键兜底。
        if (messageId != null && !messageId.isBlank()) {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            idempotentTtl = IDEMPOTENT_TTL_HOURS;
            idempotentTtlUnit = TimeUnit.HOURS;
        } else {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + "biz:" + message.getUserId() + ":" + message.getDocumentId() + ":" + message.getCollectionStat();
            idempotentTtl = IDEMPOTENT_FALLBACK_TTL_MINUTES;
            idempotentTtlUnit = TimeUnit.MINUTES;
        }

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, String.valueOf(message.getCollectionStat()), idempotentTtl, idempotentTtlUnit);
        if (Boolean.FALSE.equals(isNew)) {
            log.warn("[ArticleCollect] duplicate message ignored, messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            // 先同步用户行为，再同步聚合计数，最后异步投递通知消息。
            syncUserFoot(message);
            syncArticleCollectionCount(message);
            publishCollectNotify(message);
            log.info("[ArticleCollect] completed, messageId={}", messageId);
        } catch (Exception e) {
            log.error("[ArticleCollect] failed, messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }

    /**
     * 同步用户与文章之间的收藏关系。
     * 这里采用“先更新，更新不到再插入”的 upsert 思路，避免重复收藏场景下产生多条记录。
     */
    private void syncUserFoot(RedisCollectToDBMessage message) {
        UpdateWrapper<UserFootDO> wrapper = new UpdateWrapper<UserFootDO>()
                .set("collection_stat", message.getCollectionStat())
                .set("read_stat", defaultStat(message.getReadStat()))
                .eq("user_id", message.getUserId())
                .eq("document_id", message.getDocumentId());
        int updateRow = userFootMapper.update(null, wrapper);
        if (updateRow > 0) {
            return;
        }

        // 更新失败通常表示首次收藏，需要补建用户足迹记录。
        UserFootDO userFootDO = new UserFootDO();
        BeanUtil.copyProperties(message, userFootDO);
        userFootDO.setDocumentType(DOCUMENT_TYPE_ARTICLE);
        userFootDO.setLikeStat(0);
        userFootDO.setCommentStat(0);
        userFootDO.setReadStat(defaultStat(userFootDO.getReadStat()));
        userFootDO.setCollectionStat(defaultStat(userFootDO.getCollectionStat()));
        try {
            userFootMapper.insert(userFootDO);
        } catch (DuplicateKeyException duplicateKeyException) {
            // 并发写入下可能有其他消费者先插入，捕获唯一键冲突后重试更新即可。
            int retryUpdateRow = userFootMapper.update(null, wrapper);
            if (retryUpdateRow == 0) {
                throw duplicateKeyException;
            }
        }
    }

    /**
     * 同步文章收藏数。
     * 增加收藏时直接 +1，取消收藏时要确保计数不会被扣成负数。
     */
    private void syncArticleCollectionCount(RedisCollectToDBMessage message) {
        UpdateWrapper<ArticleDO> wrapper = new UpdateWrapper<ArticleDO>()
                .eq("id", message.getDocumentId());
        if (Integer.valueOf(1).equals(message.getCollectionStat())) {
            wrapper.setSql("collection_count = COALESCE(collection_count, 0) + 1");
        } else {
            wrapper.setSql("collection_count = CASE WHEN COALESCE(collection_count, 0) <= 0 THEN 0 ELSE COALESCE(collection_count, 0) - 1 END");
        }
        articleMapper.update(null, wrapper);
    }

    /**
     * 收藏成功后给文章作者发送通知。
     * 只有“收藏”动作才通知，取消收藏和自己收藏自己的文章都直接跳过。
     */
    private void publishCollectNotify(RedisCollectToDBMessage message) {
        if (!Integer.valueOf(1).equals(message.getCollectionStat())) {
            return;
        }
        if (message.getDocumentUserId() == null || message.getDocumentUserId().equals(message.getUserId())) {
            return;
        }

        ArticleCollectNotifyMessage notifyMessage = ArticleCollectNotifyMessage.builder()
                .articleId(message.getDocumentId())
                .notifyUserId(message.getDocumentUserId())
                .operateUserId(message.getUserId())
                .build();
        try {
            String notifyMessageId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(notifyMessageId);
            rabbitTemplate.convertAndSend("notify.direct", "article.collect.notify", notifyMessage, msg -> {
                msg.getMessageProperties().setMessageId(notifyMessageId);
                return msg;
            }, correlationData);
        } catch (Exception e) {
            log.error("[ArticleCollect] notify publish failed, documentId={}, userId={}", message.getDocumentId(), message.getUserId(), e);
        }
    }

    private int defaultStat(Integer stat) {
        return stat == null ? 0 : stat;
    }
}
