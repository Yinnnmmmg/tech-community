package com.ying.tech.community.service.ai.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.ai.service.ArticleEmbeddingService;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
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
 * 文章审核通过后同步到向量数据库的消费者。
 *
 * <p>消费者只负责消息幂等和 ACK 控制，具体的切片、向量化和写库逻辑下沉到服务层。
 */
@Slf4j
@Component
public class EmbeddingConsumer {
    /** 幂等键前缀：mq:idempotent:ai.embedding:{messageId}。 */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:ai.embedding:";
    /** 幂等标记有效期，单位：小时。 */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    /** Redis 用于记录消息幂等标记。 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    /** 向量同步服务，负责文章向量的构建与删除。 */
    @Autowired
    private ArticleEmbeddingService articleEmbeddingService;

    /**
     * 消费文章审核通过消息，并将文章内容同步到向量库。
     *
     * @param message     文章发布消息
     * @param channel     RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag 当前消息投递标签
     * @param messageId   消息唯一 ID，用于幂等控制
     * @throws IOException RabbitMQ ACK 失败时抛出
     */
    @RabbitListener(queues = "ai.embedding.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        if (messageId != null) {
            // 基于 messageId 做幂等拦截，避免同一文章重复写入向量库。
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[Embedding] duplicate message, skip, messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // 交给服务层执行文章切片、向量化与向量库写入。
            articleEmbeddingService.rebuildArticleEmbedding(message.getArticleId());
        } catch (Exception e) {
            log.error("[Embedding] handle failed, articleId={}, error={}",
                    message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }

        // 仅在业务处理成功后手动 ACK。
        channel.basicAck(deliveryTag, false);
    }
}
