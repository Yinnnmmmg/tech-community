package com.ying.tech.community.service.comment.consumer;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.notifyMsg.message.CommentLikeNotifyMessage;
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
 * 评论点赞消息消费者。
 *
 * <p>负责把评论点赞行为同步到 user_foot、comment 主表，并在需要时发送点赞通知。
 */
@Component
@Slf4j
public class CommentLikeConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:comment.like:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;
    private static final long IDEMPOTENT_FALLBACK_TTL_MINUTES = 5;
    private static final int DOCUMENT_TYPE_COMMENT = 2;

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserFootMapper userFootMapper;
    private final CommentMapper commentMapper;
    private final RabbitTemplate rabbitTemplate;

    public CommentLikeConsumer(RedisTemplate<String, Object> redisTemplate,
                               UserFootMapper userFootMapper,
                               CommentMapper commentMapper,
                               RabbitTemplate rabbitTemplate) {
        this.redisTemplate = redisTemplate;
        this.userFootMapper = userFootMapper;
        this.commentMapper = commentMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 消费评论点赞消息并完成落库。
     */
    @RabbitListener(queues = "comment.like.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handleCommentLike(RedisLikeToDBMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId)
            throws IOException {
        log.info("[CommentLike] receive, userId={}, commentId={}, commentAuthorId={}, likeStat={}",
                message.getUserId(), message.getDocumentId(), message.getDocumentUserId(), message.getLikeStat());

        String idempotentKey;
        long ttl;
        TimeUnit ttlUnit;
        //优先使用 messageId 做幂等，缺失时退化到业务键兜底
        if (messageId != null && !messageId.isBlank()) {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            ttl = IDEMPOTENT_TTL_HOURS;
            ttlUnit = TimeUnit.HOURS;
        } else {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + "biz:" + message.getUserId() + ":" + message.getDocumentId()
                    + ":" + message.getLikeStat();
            ttl = IDEMPOTENT_FALLBACK_TTL_MINUTES;
            ttlUnit = TimeUnit.MINUTES;
        }

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, String.valueOf(message.getLikeStat()), ttl, ttlUnit);
        if (Boolean.FALSE.equals(isNew)) {
            log.warn("[CommentLike] duplicate message ignored, messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            syncUserFoot(message);
            syncCommentLikeCount(message);
            publishLikeNotify(message);
            log.info("[CommentLike] completed, messageId={}", messageId);
        } catch (Exception e) {
            log.error("[CommentLike] failed, messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }

    /**
     * 同步用户和评论之间的点赞关系。
     */
    private void syncUserFoot(RedisLikeToDBMessage message) {
        UpdateWrapper<UserFootDO> wrapper = new UpdateWrapper<UserFootDO>()
                .set("like_stat", defaultStat(message.getLikeStat()))
                .eq("user_id", message.getUserId())
                .eq("document_id", message.getDocumentId())
                .eq("document_type", DOCUMENT_TYPE_COMMENT);
        int updateRow = userFootMapper.update(null, wrapper);
        if (updateRow > 0) {
            return;
        }

        //更新不到通常是首次点赞，需要补建评论维度的用户足迹
        UserFootDO userFootDO = new UserFootDO();
        userFootDO.setUserId(message.getUserId());
        userFootDO.setDocumentId(message.getDocumentId());
        userFootDO.setDocumentType(DOCUMENT_TYPE_COMMENT);
        userFootDO.setDocumentUserId(message.getDocumentUserId());
        userFootDO.setCollectionStat(0);
        userFootDO.setReadStat(0);
        userFootDO.setCommentStat(0);
        userFootDO.setLikeStat(defaultStat(message.getLikeStat()));
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
     * 同步评论主表里的点赞总数。
     */
    private void syncCommentLikeCount(RedisLikeToDBMessage message) {
        UpdateWrapper<CommentDO> wrapper = new UpdateWrapper<CommentDO>()
                .eq("id", message.getDocumentId());
        if (Integer.valueOf(1).equals(message.getLikeStat())) {
            wrapper.setSql("like_count = COALESCE(like_count, 0) + 1");
        } else {
            wrapper.setSql("like_count = CASE WHEN COALESCE(like_count, 0) <= 0 THEN 0 ELSE COALESCE(like_count, 0) - 1 END");
        }
        commentMapper.update(null, wrapper);
    }

    /**
     * 点赞成功后给评论作者发送通知。
     */
    private void publishLikeNotify(RedisLikeToDBMessage message) {
        if (!Integer.valueOf(1).equals(message.getLikeStat())) {
            return;
        }
        if (message.getDocumentUserId() == null || message.getDocumentUserId().equals(message.getUserId())) {
            return;
        }

        CommentLikeNotifyMessage notifyMessage = CommentLikeNotifyMessage.builder()
                .commentId(message.getDocumentId())
                .notifyUserId(message.getDocumentUserId())
                .operateUserId(message.getUserId())
                .build();
        try {
            String notifyMessageId = UUID.randomUUID().toString();
            CorrelationData correlationData = new CorrelationData(notifyMessageId);
            rabbitTemplate.convertAndSend("notify.direct", "comment.like.notify", notifyMessage, msg -> {
                msg.getMessageProperties().setMessageId(notifyMessageId);
                return msg;
            }, correlationData);
        } catch (Exception e) {
            log.error("[CommentLike] notify publish failed, commentId={}, userId={}",
                    message.getDocumentId(), message.getUserId(), e);
        }
    }

    /**
     * 统一把空状态转换成 0。
     */
    private int defaultStat(Integer stat) {
        return stat == null ? 0 : stat;
    }
}
