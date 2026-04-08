package com.ying.tech.community.service.article.consumer.arti_pub_consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.entity.es.ArticleDocument;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.repository.ArticleESRepository;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 文章发布后同步到 ES 的消费者。
 *
 * 处理流程：
 * 1. 消费发布消息；
 * 2. 从 MySQL 查询文章主表、最新详情和作者信息；
 * 3. 组装 ES 文档并写入索引；
 * 4. 成功后手动 ACK。
 */
@Slf4j
@Component
public class ArticlePublishEsSyncConsumer {

    /** 幂等键前缀：mq:idempotent:article.publish.es:{messageId} */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.publish.es:";

    /** 幂等键过期时间（小时） */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleDetailMapper articleDetailMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleESRepository articleESRepository;

    /**
     * 消费文章发布消息并同步到 ES。
     *
     * @param message 发布消息
     * @param channel RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag 当前消息投递标签
     * @param messageId 消息唯一 ID（用于幂等）
     */
    @RabbitListener(queues = "article.publish.es.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        log.info("[ArticlePublishES] receive, articleId={}, messageId={}", message.getArticleId(), messageId);

        // 幂等性检查：对每个 messageId 仅处理一次
        if (messageId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[ArticlePublishES] duplicate message, skip, messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            // 1. 查询文章主记录
            ArticleDO article = articleMapper.selectById(message.getArticleId());
            if (article == null) {
                throw new IllegalStateException("article not found, articleId=" + message.getArticleId());
            }

            // 2. 按版本倒序查询最新文章详情
            ArticleDetailDO detail = articleDetailMapper.selectOne(
                new LambdaQueryWrapper<ArticleDetailDO>()
                    .eq(ArticleDetailDO::getArticleId, message.getArticleId())
                    .orderByDesc(ArticleDetailDO::getVersion)
                    .last("limit 1")
            );

            // 3. 查询作者信息
            UserDO author = userMapper.selectById(article.getUserId());

            // 4. 构建 ES 文档
            ArticleDocument document = new ArticleDocument();
            document.setId(article.getId());
            document.setTitle(article.getTitle());
            document.setContent(detail != null ? detail.getContent() : null);
            document.setAuthor(author != null ? author.getUsername() : null);
            document.setAuthorId(article.getUserId());
            document.setReadCount(article.getViewCount());
            document.setLikeCount(article.getLikeCount());
            document.setPublishTime(message.getPublishTime());
            document.setCreateTime(toEpochMillis(article.getCreateTime()));
            document.setUpdateTime(toEpochMillis(article.getUpdateTime()));

            // 5. 确保索引存在并写入文档
            articleESRepository.ensureIndexExists();
            articleESRepository.index(document);
            log.info("[ArticlePublishES] sync success, articleId={}", message.getArticleId());
        } catch (Exception e) {
            // 抛出异常以触发监听器容器的重试/死信处理
            log.error("[ArticlePublishES] handle failed, articleId={}, error={}",
                message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }

        // 仅在处理成功后手动 ACK
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 将 LocalDateTime 转为毫秒时间戳。
     */
    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}