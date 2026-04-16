package com.ying.tech.community.service.notifyMsg.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.message.UserFollowNotifyMessage;
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
 * 用户关注通知消费者。
 * 在关注关系建立后生成站内通知，提醒被关注用户有新的粉丝。
 */
@Slf4j
@Component
public class UserFollowNotifyConsumer {
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:user.follow.notify:";
    private static final long IDEMPOTENT_TTL_HOURS = 24;
    private static final long IDEMPOTENT_FALLBACK_TTL_MINUTES = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotifyMsgMapper notifyMsgMapper;

    public UserFollowNotifyConsumer(RedisTemplate<String, Object> redisTemplate,
                                    NotifyMsgMapper notifyMsgMapper) {
        this.redisTemplate = redisTemplate;
        this.notifyMsgMapper = notifyMsgMapper;
    }

    @RabbitListener(queues = "user.follow.notify.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(UserFollowNotifyMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        String idempotentKey;
        long ttl;
        TimeUnit ttlUnit;
        // 优先按 MQ messageId 做幂等；缺失时退回到“关注者+被关注者”的业务组合键。
        if (messageId != null && !messageId.isBlank()) {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            ttl = IDEMPOTENT_TTL_HOURS;
            ttlUnit = TimeUnit.HOURS;
        } else {
            idempotentKey = IDEMPOTENT_KEY_PREFIX + "biz:" + message.getFollowerId() + ":" + message.getNotifyUserId();
            ttl = IDEMPOTENT_FALLBACK_TTL_MINUTES;
            ttlUnit = TimeUnit.MINUTES;
        }

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", ttl, ttlUnit);
        if (Boolean.FALSE.equals(isNew)) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            // 消息体没有有效昵称时使用默认占位文案，保证通知内容始终可读。
            String followerName = message.getFollowerName() == null || message.getFollowerName().isBlank()
                    ? "A user"
                    : message.getFollowerName();
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                    .relatedId(message.getFollowerId())
                    .notifyUserId(message.getNotifyUserId())
                    .operateUserId(message.getFollowerId())
                    .msg(followerName + "关注了你")
                    .type(NotifyMsgConstants.Type.FOLLOW)
                    .state(NotifyMsgConstants.State.UNREAD)
                    .build();
            notifyMsgMapper.insert(notifyMsg);
            log.info("[UserFollowNotify] inserted, followerId={}, notifyUserId={}",
                    message.getFollowerId(), message.getNotifyUserId());
        } catch (Exception e) {
            log.error("[UserFollowNotify] failed, followerId={}, notifyUserId={}",
                    message.getFollowerId(), message.getNotifyUserId(), e);
            throw new RuntimeException(e);
        }

        channel.basicAck(deliveryTag, false);
    }
}
