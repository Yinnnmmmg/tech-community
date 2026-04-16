package com.ying.tech.community.service.notifyMsg.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.message.ArticleCollectNotifyMessage;
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
 * 文章收藏通知消费者。
 * 接收收藏事件后生成站内通知消息，提醒文章作者有新的收藏行为。
 */
@Slf4j
@Component
public class ArticleCollectNotifyConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.collect.notify:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotifyMsgMapper notifyMsgMapper;

    public ArticleCollectNotifyConsumer(RedisTemplate<String, Object> redisTemplate,
                                        NotifyMsgMapper notifyMsgMapper) {
        this.redisTemplate = redisTemplate;
        this.notifyMsgMapper = notifyMsgMapper;
    }

    @RabbitListener(queues = "article.collect.notify.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticleCollectNotifyMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        // 使用 messageId 作为幂等键，避免消息重复投递导致通知表插入重复记录。
        if (messageId != null && !messageId.isBlank()) {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(IDEMPOTENT_KEY_PREFIX + messageId, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // 通知表只记录面向被通知人的必要信息，展示文案在此处直接生成。
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                    .relatedId(message.getArticleId())
                    .notifyUserId(message.getNotifyUserId())
                    .operateUserId(message.getOperateUserId())
                    .msg("你的文章收到了新的收藏")
                    .type(NotifyMsgConstants.Type.FAVORITE)
                    .state(NotifyMsgConstants.State.UNREAD)
                    .build();
            notifyMsgMapper.insert(notifyMsg);
            log.info("[ArticleCollectNotify] inserted, articleId={}", message.getArticleId());
        } catch (Exception e) {
            log.error("[ArticleCollectNotify] failed, articleId={}", message.getArticleId(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }
}
