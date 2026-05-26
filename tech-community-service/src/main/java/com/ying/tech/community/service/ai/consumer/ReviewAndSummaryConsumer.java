package com.ying.tech.community.service.ai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.service.ai.entity.PublishAiResult;
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

import static com.ying.tech.community.core.constants.AiConstants.ARTICLE_REVIEW_SYSTEM_PROMPT;

/**
 * AI审核与摘要生成消费者
 *
 * <p>负责消费文章发布审核队列的消息，调用AI大模型进行内容安全审核和摘要生成。
 * 主要业务流程：
 * 1. 幂等性检查：基于messageId防止重复处理同一条消息
 * 2. 获取文章内容：从数据库查询文章正文
 * 3. AI模型调用：调用DeepSeek大模型进行内容审核和摘要生成
 * 4. 结果处理：
 *    - 审核不通过：更新文章状态为REJECTED(2)，发送发布失败通知
 *    - 审核通过：更新文章状态为APPROVED(1)，设置AI生成的摘要，广播到下游消费方
 * 5. 消息确认：手动ACK确保消息可靠消费
 *
 * <p>下游广播：审核通过的文章会广播到article.fanout交换机，触发以下消费者：
 *   - ArticlePublishTimelineConsumer: 更新Redis时间轴ZSet
 *   - ArticlePublishNotifyConsumer: 发送系统通知
 *   - ArticlePublishEsSyncConsumer: 同步数据到Elasticsearch
 *
 * <p>技术要点：
 *   - 幂等性：基于Redis的setIfAbsent实现，防止网络重试导致的重复消费
 *   - 异常处理：捕获所有异常并抛出RuntimeException，触发Spring AMQP重试机制
 *   - 重试策略：配置了3次重试，失败后消息进入死信队列
 *   - 事务边界：不涉及数据库事务，每个操作独立提交
 */
@Slf4j
@Component
public class ReviewAndSummaryConsumer {
    /** RabbitTemplate：用于发送MQ消息，包括审核通过后的广播和审核不通过的通知 */
    @Autowired
    private RabbitTemplate rabbitTemplate;
    /** ArticleDetailMapper：用于查询文章详情表，获取文章正文内容供AI审核 */
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    /** ArticleMapper：用于更新文章状态和摘要，包括审核通过和审核不通过的场景 */
    @Autowired
    private ArticleMapper articleMapper;
    /** DeepSeek大模型客户端：用于调用AI模型进行内容安全审核和摘要生成 */
    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    // 幂等 Redis Key 前缀
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:review-and-summary:";
    // 幂等 Key 过期时间（小时）
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    /** RedisTemplate：用于幂等性检查，基于messageId防止重复消费 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理文章发布审核消息
     *
     * <p>消费article.publish.review.queue队列的消息，执行AI审核和摘要生成流程。
     * 使用manualAckListenerContainerFactory容器工厂，支持手动ACK和重试机制。
     *
     * @param message     文章发布消息体，包含文章ID、作者ID、发布时间等信息
     * @param channel     RabbitMQ通道，用于手动确认消息
     * @param deliveryTag 消息投递标签，用于消息确认
     * @param messageId   消息ID，用于幂等性检查，可选参数
     * @throws IOException RabbitMQ通道操作异常
     * @throws RuntimeException 业务处理异常，会触发Spring AMQP重试机制
     */
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
            // 1. 获取文章标题和正文内容：供AI模型审核
            String title = articleMapper.selectById(message.getArticleId()).getTitle();
            QueryWrapper<ArticleDetailDO> queryWrapper = new QueryWrapper<ArticleDetailDO>()
                    .select("content")
                    .eq("article_id", message.getArticleId());
            String content = articleDetailMapper.selectOne(queryWrapper).getContent();

            // 2. 调用AI大模型进行安全审核和摘要生成
            //    使用系统提示词约束模型行为，将文章正文作为用户输入
            PublishAiResult result = chatClient.prompt()
                    .system(ARTICLE_REVIEW_SYSTEM_PROMPT)
                    .user(u -> u.text("文章标题和正文如下：\n{content},{title}").param("content", content).param("title",title))
                    .call()
                    .entity(PublishAiResult.class);

            // 3. 检查AI模型返回结果：确保模型返回了有效结果
            if (result == null) {
                throw new RuntimeException("模型返回结果为空");
            }

            // 4. 审核不通过处理：AI判定内容不安全
            //    a. 更新文章状态为REJECTED(2)
            //    b. 发送发布失败通知到notify.direct交换机
            //    c. 确认消息并返回，不进行后续处理
            if (!result.isSafe()) {
                UpdateWrapper<ArticleDO> updateWrapper = new UpdateWrapper<ArticleDO>()
                        .eq("id", message.getArticleId())
                        .set("status", PublishStatusConstants.REJECTED)
                        .set("reject_reason", result.reason());
                articleMapper.update(updateWrapper);
                // 将审核不通过原因传递给通知消费者
                message.setReason(result.reason());
                rabbitTemplate.convertAndSend("notify.direct", "notify.publish.fail", message);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 5. 审核通过处理：AI判定内容安全
            //    a. 更新文章摘要为AI生成的摘要
            //    b. 更新文章状态为APPROVED(1)
            //    c. 记录处理成功日志
            UpdateWrapper<ArticleDO> updateWrapper = new UpdateWrapper<ArticleDO>()
                    .eq("id", message.getArticleId())
                    .set("summary", result.summary())
                    .set("status", PublishStatusConstants.APPROVED);
            articleMapper.update(updateWrapper);
            log.info("[ReviewAndSummary] handle success, articleId={}", message.getArticleId());

            // 6. 广播消息到下游消费者：审核通过后，通知下游业务方
            //    a. 使用article.fanout交换机广播消息（无路由键）
            //    b. 设置messageId到消息属性，供下游消费者幂等性检查
            //    c. 使用CorrelationData关联消息，支持ConfirmCallback旁路日志
            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend("article.fanout", "", message, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                return msg;
            }, correlationData);
            log.info("[ReviewAndSummary] broadcast success, articleId={}", message.getArticleId());

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 异常处理：记录错误日志并抛出RuntimeException
            // Spring AMQP会根据配置的重试策略进行重试，重试失败后消息进入死信队列
            log.error("[ReviewAndSummary] handle failed, articleId={}, error={}", message.getArticleId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}