package com.ying.tech.community.service.notifyMsg.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.message.CommentLikeNotifyMessage;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 评论点赞通知消费者。
 */
@Slf4j
@Component
public class CommentLikeNotifyConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:comment.like.notify:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotifyMsgMapper notifyMsgMapper;

    public CommentLikeNotifyConsumer(RedisTemplate<String, Object> redisTemplate,
                                     NotifyMsgMapper notifyMsgMapper) {
        this.redisTemplate = redisTemplate;
        this.notifyMsgMapper = notifyMsgMapper;
    }

    /**
     * 处理评论点赞通知消息并落库到消息中心。
     */
    @RabbitListener(queues = "comment.like.notify.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(CommentLikeNotifyMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        //通过 Redis 幂等键避免同一条点赞通知被重复落库
        if (messageId != null && !messageId.isBlank()) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(IDEMPOTENT_KEY_PREFIX + messageId, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                    .relatedId(message.getCommentId())
                    .notifyUserId(message.getNotifyUserId())
                    .operateUserId(message.getOperateUserId())
                    .msg("你的评论收到了新的点赞")
                    .type(NotifyMsgConstants.Type.LIKE)
                    .state(NotifyMsgConstants.State.UNREAD)
                    .build();
            notifyMsgMapper.insert(notifyMsg);
            log.info("[CommentLikeNotify] inserted, commentId={}", message.getCommentId());
        } catch (Exception e) {
            log.error("[CommentLikeNotify] failed, commentId={}", message.getCommentId(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }
}
