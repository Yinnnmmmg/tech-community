package com.ying.tech.community.service.article.consumer.arti_pub_consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;

import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 文章发布后：写入系统通知（SYSTEM 类型）。
 */
@Slf4j
@Component
public class ArticlePublishNotifyConsumer {

    /**
     * 幂等性 Redis Key 前缀
     */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.publish.notify:";
    /**
     * 幂等性 Key 的过期时间（小时）
     */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    /**
     * 处理文章发布消息，创建系统通知
     *
     * @param message     文章发布消息体
     * @param channel     RabbitMQ通道，用于手动确认
     * @param deliveryTag 消息投递标签
     * @param messageId   消息ID，用于幂等性处理（可选）
     * @throws IOException RabbitMQ通道操作异常
     */
    @RabbitListener(queues = "article.publish.notify.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        log.info("[ArticlePublishNotify] receive, articleId={}, messageId={}", message.getArticleId(), messageId);

        // 幂等性检查：防止重复处理同一条消息
        if (messageId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[ArticlePublishNotify] duplicate message, skip, messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // 核心业务：构建系统通知对象
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                .relatedId(message.getArticleId())           // 关联的文章ID
                .notifyUserId(message.getAuthorId())         // 通知的用户（文章作者）
                .operateUserId(message.getAuthorId())        // 操作的用户（文章作者）
                .msg("您的文章已成功发布！")                  // 通知消息内容
                .type(NotifyMsgConstants.Type.SYSTEM)        // 通知类型：系统通知
                .state(NotifyMsgConstants.State.UNREAD)      // 通知状态：未读
                .build();

            // 将通知保存到数据库
            notifyMsgMapper.insert(notifyMsg);
            log.info("[ArticlePublishNotify] notify insert success, articleId={}", message.getArticleId());
        } catch (Exception e) {
            log.error("[ArticlePublishNotify] handle failed, articleId={}, error={}",
                message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }

        // 手动确认消息，确保消息被正确消费
        channel.basicAck(deliveryTag, false);
    }
}
