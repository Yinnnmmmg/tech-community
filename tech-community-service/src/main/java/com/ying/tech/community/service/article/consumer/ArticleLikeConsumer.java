package com.ying.tech.community.service.article.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.notifyMsg.message.ArticleLikeNotifyMessage;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.message.RedisLikeToDBMessage;
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
 * 文章点赞消息消费者。
 *
 * <p>负责消费点赞/取消点赞消息，完成点赞关系落库、文章点赞数同步，以及点赞通知投递。
 */
@Component
@Slf4j
public class ArticleLikeConsumer {
    /** 幂等键前缀，优先基于 messageId 拦截重复消息。 */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.like:";
    /** 基于 messageId 的幂等标记过期时间，单位：小时。 */
    private static final long IDEMPOTENT_TTL_HOURS = 24;
    /** messageId 缺失时兜底幂等键的有效期，单位：分钟。 */
    private static final long IDEMPOTENT_FALLBACK_TTL_MINUTES = 5;
    /** user_foot 表中的文章类型值。 */
    private static final int DOCUMENT_TYPE_ARTICLE = 1;

    /** Redis 用于幂等标记和行为缓存。 */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 用户足迹 Mapper，用于同步点赞关系。 */
    private final UserFootMapper userFootMapper;
    /** 文章 Mapper，用于同步点赞计数。 */
    private final ArticleMapper articleMapper;
    /** RabbitMQ 模板，用于投递点赞通知消息。 */
    private final RabbitTemplate rabbitTemplate;

    public ArticleLikeConsumer(RedisTemplate<String, Object> redisTemplate,
                               UserFootMapper userFootMapper,
                               ArticleMapper articleMapper,
                               RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.userFootMapper = userFootMapper;
        this.articleMapper = articleMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 消费点赞消息并完成落库。
     *
     * @param message     点赞消息
     * @param channel     RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag 当前消息投递标签
     * @param messageId   消息唯一 ID，用于幂等控制
     * @throws IOException RabbitMQ ACK 失败时抛出
     */
    @RabbitListener(queues = "article.like.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handleArticleLike(RedisLikeToDBMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId)
            throws IOException {
        log.info("[ArticleLike] receive, userId={}, documentId={}, documentUserId={}, readStat={}, likeStat={}",
                message.getUserId(), message.getDocumentId(), message.getDocumentUserId(), message.getReadStat(), message.getLikeStat());

        String idempotentKey;
        long idempotentTtl;
        TimeUnit idempotentTtlUnit;
        if (messageId != null && !messageId.isBlank()) {
            // 优先使用 MQ messageId 做幂等控制。
            idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            idempotentTtl = IDEMPOTENT_TTL_HOURS;
            idempotentTtlUnit = TimeUnit.HOURS;
        } else {
            // messageId 缺失时退化为业务键兜底，尽量避免短时间重复消费。
            idempotentKey = IDEMPOTENT_KEY_PREFIX + "biz:" + message.getUserId() + ":" + message.getDocumentId() + ":" + message.getLikeStat();
            idempotentTtl = IDEMPOTENT_FALLBACK_TTL_MINUTES;
            idempotentTtlUnit = TimeUnit.MINUTES;
        }

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, String.valueOf(message.getLikeStat()), idempotentTtl, idempotentTtlUnit);
        if (Boolean.FALSE.equals(isNew)) {
            log.warn("[ArticleLike] duplicate message ignored, messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            syncUserFoot(message);
            syncArticleLikeCount(message);
            publishLikeNotify(message);
            log.info("[ArticleLike] completed, messageId={}", messageId);
        } catch (Exception e) {
            log.error("[ArticleLike] failed, messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }

    /**
     * 同步用户与文章之间的点赞关系。
     *
     * <p>先尝试更新，未命中时再插入；若插入阶段遇到唯一键竞争，则回退为更新。
     */
    private void syncUserFoot(RedisLikeToDBMessage message) {
        UpdateWrapper<UserFootDO> wrapper = new UpdateWrapper<UserFootDO>()
                .set("like_stat", message.getLikeStat())
                .set("read_stat", defaultStat(message.getReadStat()))
                .eq("user_id", message.getUserId())
                .eq("document_id", message.getDocumentId())
                .eq("document_type", DOCUMENT_TYPE_ARTICLE);
        int updateRow = userFootMapper.update(null, wrapper);
        if (updateRow > 0) {
            return;
        }

        UserFootDO userFootDO = new UserFootDO();
        BeanUtil.copyProperties(message, userFootDO);
        userFootDO.setDocumentType(DOCUMENT_TYPE_ARTICLE);
        userFootDO.setCollectionStat(0);
        userFootDO.setCommentStat(0);
        userFootDO.setReadStat(defaultStat(userFootDO.getReadStat()));
        userFootDO.setLikeStat(defaultStat(userFootDO.getLikeStat()));
        try {
            userFootMapper.insert(userFootDO);
        } catch (DuplicateKeyException duplicateKeyException) {
            int retryUpdateRow = userFootMapper.update(null, wrapper);
            if (retryUpdateRow == 0) {
                throw duplicateKeyException;
            }
        }
    }

    /**
     * 同步文章主表中的点赞总数。
     */
    private void syncArticleLikeCount(RedisLikeToDBMessage message) {
        UpdateWrapper<ArticleDO> wrapper = new UpdateWrapper<ArticleDO>()
                .eq("id", message.getDocumentId());
        if (Integer.valueOf(1).equals(message.getLikeStat())) {
            wrapper.setSql("like_count = COALESCE(like_count, 0) + 1");
        } else {
            wrapper.setSql("like_count = CASE WHEN COALESCE(like_count, 0) <= 0 THEN 0 ELSE COALESCE(like_count, 0) - 1 END");
        }
        articleMapper.update(null, wrapper);
    }

    /**
     * 在点赞成功后投递通知消息。
     *
     * <p>取消点赞和给自己点赞都不会发送通知。
     */
    private void publishLikeNotify(RedisLikeToDBMessage message) {
        if (!Integer.valueOf(1).equals(message.getLikeStat())) {
            return;
        }
        if (message.getDocumentUserId() == null || message.getDocumentUserId().equals(message.getUserId())) {
            return;
        }

        ArticleLikeNotifyMessage notifyMessage = ArticleLikeNotifyMessage.builder()
                .articleId(message.getDocumentId())
                .notifyUserId(message.getDocumentUserId())
                .operateUserId(message.getUserId())
                .build();
        try {
            String notifyMessageId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(notifyMessageId);
            rabbitTemplate.convertAndSend("notify.direct", "article.like.notify", notifyMessage, msg -> {
                msg.getMessageProperties().setMessageId(notifyMessageId);
                return msg;
            }, correlationData);
        } catch (Exception e) {
            log.error("[ArticleLike] notify publish failed, documentId={}, userId={}", message.getDocumentId(), message.getUserId(), e);
        }
    }

    /**
     * 将空状态统一转换为 0，便于落库。
     */
    private int defaultStat(Integer stat) {
        return stat == null ? 0 : stat;
    }
}
