package com.ying.tech.community.service.ai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文章审核后同步到向量数据库的消费者。
 * <p>
 * 处理流程：
 * 1. 消费文章发布消息；
 * 2. 获取文章内容并生成向量；
 * 3. 向量写入向量数据库；
 * 4. 成功后手动 ACK。
 */
@Slf4j
@Component
public class EmbeddingConsumer {
    /** 幂等键键前缀：mq:idempotent:ai.embedding:{messageId}*/
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:ai.embedding:";
    /** 幂等键过期时间（小时）*/
    private static final long IDEMPOTENT_TTL_HOURS = 24;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    @Qualifier("bailianEmbeddingModel")
    private EmbeddingModel embeddingModel;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private ArticleMapper articleMapper;

    /**
     * 消费文章审核消息并同步向量到向量数据库
     *
     * @param message     文章发布消息
     * @param channel     RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag 当前消息投递标签
     * @param messageId   消息唯一 ID（用于幂等）
     */
    @RabbitListener(queues = "ai.embedding.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        //幂等性检查：对每个 messageId 仅处理一次
        if (messageId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if(Boolean.FALSE.equals(isNew)){
                log.warn("[Embedding] duplicate message, skip, messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            //查询文章标题和内容
            String title = articleMapper.selectById(message.getArticleId()).getTitle();
            QueryWrapper<ArticleDetailDO> queryWrapper = new QueryWrapper<ArticleDetailDO>()
                    .select("content")
                    .eq("article_id", message.getArticleId());
            String content = articleDetailMapper.selectOne(queryWrapper).getContent();
            //分割、向量化、存储
            //主体
            String rawText = title + "\n\n" + content;
            // 创建 Document 对象，添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("articleId", message.getArticleId());
            metadata.put("title", title);
            metadata.put("authorId", message.getAuthorId());
            Document document = new Document(rawText, metadata);
            //分割
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(List.of(document));
            vectorStore.add(chunks);


        } catch (Exception e) {
            // 抛出异常以触发监听器容器的重试/死信处理
            log.error("[Embedding] handle failed, articleId={}, error={}",
                    message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
        //仅在处理成功后手动 ACK
        channel.basicAck(deliveryTag, false);
    }
}
