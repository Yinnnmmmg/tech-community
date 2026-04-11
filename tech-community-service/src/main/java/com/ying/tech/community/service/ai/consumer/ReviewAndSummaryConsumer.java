package com.ying.tech.community.service.ai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.ArticleStatusConstants;
import com.ying.tech.community.service.ai.entity.ArticleAiResult;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.ying.tech.community.core.constants.AiConstants.SYSTEM_PROMPT;

/**
 * 文章发布后，调用 AI 完成审核与摘要生成。
 * 审核通过：更新摘要和状态，并广播到下游消费方。
 * 审核不通过：更新状态并发送发布失败通知。
 */
@Slf4j
@Component
public class ReviewAndSummaryConsumer {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    // 幂等 Redis Key 前缀
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:review-and-summary:";
    // 幂等 Key 过期时间（小时）
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = "article.publish.review.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
        log.info("[ReviewAndSummary] receive, articleId={}, messageId={}", message.getArticleId(), messageId);

        // 幂等检查：同一个 messageId 只处理一次
        if (messageId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("[ReviewAndSummary] duplicate message, skip, messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }

        try {
            QueryWrapper<ArticleDetailDO> queryWrapper = new QueryWrapper<ArticleDetailDO>()
                    .select("content")
                    .eq("article_id", message.getArticleId());
            String content = articleDetailMapper.selectOne(queryWrapper).getContent();

            ArticleAiResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(u -> u.text("文章正文如下：\n{content}").param("content", content))
                    .call()
                    .entity(ArticleAiResult.class);

            if (result == null) {
                throw new RuntimeException("模型返回结果为空");
            }

            if (!result.isSafe()) {
                UpdateWrapper<ArticleDO> updateWrapper = new UpdateWrapper<ArticleDO>()
                        .eq("id", message.getArticleId())
                        .set("status", ArticleStatusConstants.REJECTED);
                articleMapper.update(updateWrapper);
                rabbitTemplate.convertAndSend("notify.direct", "notify.publish.fail", message);
                channel.basicAck(deliveryTag, false);
                return;
            }

            UpdateWrapper<ArticleDO> updateWrapper = new UpdateWrapper<ArticleDO>()
                    .eq("id", message.getArticleId())
                    .set("summary", result.summary())
                    .set("status", ArticleStatusConstants.APPROVED);
            articleMapper.update(updateWrapper);
            log.info("[ReviewAndSummary] handle success, articleId={}", message.getArticleId());

            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend("article.fanout", "", message, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                return msg;
            }, correlationData);
            log.info("[ReviewAndSummary] broadcast success, articleId={}", message.getArticleId());

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[ReviewAndSummary] handle failed, articleId={}, error={}", message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}