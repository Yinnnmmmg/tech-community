package com.ying.tech.community.service.notifyMsg.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.message.ArticleLikeNotifyMessage;
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
 * 文章点赞通知消费者。
 * 负责把点赞消息转换为站内通知记录，供消息中心查询展示。
 */
@Slf4j
@Component
public class ArticleLikeNotifyConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.like.notify:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotifyMsgMapper notifyMsgMapper;

    public ArticleLikeNotifyConsumer(RedisTemplate<String, Object> redisTemplate,
                                     NotifyMsgMapper notifyMsgMapper) {
        this.redisTemplate = redisTemplate;
        this.notifyMsgMapper = notifyMsgMapper;
    }

    @RabbitListener(queues = "article.like.notify.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticleLikeNotifyMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        // 通过 Redis 做幂等控制，避免重复消费产生多条“被点赞”通知。
        if (messageId != null && !messageId.isBlank()) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(IDEMPOTENT_KEY_PREFIX + messageId, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // 点赞通知与收藏通知结构一致，只是消息类型和文案不同。
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                    .relatedId(message.getArticleId())
                    .notifyUserId(message.getNotifyUserId())
                    .operateUserId(message.getOperateUserId())
                    .msg("你的文章收到了新的点赞")
                    .type(NotifyMsgConstants.Type.LIKE)
                    .state(NotifyMsgConstants.State.UNREAD)
                    .build();
            notifyMsgMapper.insert(notifyMsg);
            log.info("[ArticleLikeNotify] inserted, articleId={}", message.getArticleId());
        } catch (Exception e) {
            log.error("[ArticleLikeNotify] failed, articleId={}", message.getArticleId(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }
}
