package com.ying.tech.community.service.article.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.mapper.NotifyMsgMapper;
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
 * 文章发布消息消费者
 *
 * <p>消费队列：article.publish.queue（绑定到 article.fanout 广播交换机）
 *
 * <p>职责：
 * <ol>
 *   <li>幂等拦截：Redis SETNX 防止重复消费</li>
 *   <li>写入 Redis ZSet 时间轴，供首页游标分页使用</li>
 *   <li>写入系统通知（SYSTEM 类型），通知作者文章发布成功</li>
 *   <li>手动 ACK/NACK：成功 basicAck；异常 basicNack(requeue=false) 路由至死信队列</li>
 * </ol>
 */
@Slf4j
@Component
public class ArticlePublishConsumer {

    /** 幂等 Key 前缀，TTL 24 小时（消息在一天内不会重复投递） */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.publish:";
    private static final long   IDEMPOTENT_TTL_HOURS  = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    @RabbitListener(queues = "article.publish.queue" , containerFactory = "manualAckListenerContainerFactory")
    public void handleArticlePublish(ArticlePublishMessage message,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                      @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId)
            throws IOException {

        log.info("[ArticlePublish] 收到发布消息, articleId: {}, messageId: {}", message.getArticleId(), messageId);

        // ① 幂等拦截：同一 messageId 只处理一次
        if (messageId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[ArticlePublish] 幂等拦截，消息已处理，messageId: {}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // ② 写入 Redis ZSet 时间轴（score = 发布时间戳，游标分页按 score 降序读取）
            redisTemplate.opsForZSet().add(
                    RedisConstants.TECH_COMMUNITY_ARTICLE_LIST,
                    message.getArticleId().toString(),
                    message.getPublishTime());
            log.info("[ArticlePublish] ZSet 时间轴写入成功, articleId: {}, score: {}",
                    message.getArticleId(), message.getPublishTime());

            // ③ 写入系统通知，告知作者文章发布成功
            NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                    .relatedId(message.getArticleId())
                    .notifyUserId(message.getAuthorId())
                    .operateUserId(message.getAuthorId())
                    .msg("您的文章已成功发布！")
                    .type(NotifyMsgConstants.Type.SYSTEM)
                    .state(NotifyMsgConstants.State.UNREAD)
                    .build();
            notifyMsgMapper.insert(notifyMsg);
            log.info("[ArticlePublish] 系统通知写入成功, articleId: {}", message.getArticleId());

        } catch (Exception e) {
            log.error("[ArticlePublish] 消息处理失败, articleId: {}, error: {}，等待 Spring Retry 重试",
                    message.getArticleId(), e.getMessage(), e);
            // 重新抛出异常，由 RetryInterceptorBuilder 接管重试（共3次）
            // 3次全部失败后，RejectAndDontRequeueRecoverer 触发 DLX 路由至 article.publish.dlq
            throw new RuntimeException(e);
        }

        // ④ 业务全部成功后手动 ACK
        channel.basicAck(deliveryTag, false);
    }
}