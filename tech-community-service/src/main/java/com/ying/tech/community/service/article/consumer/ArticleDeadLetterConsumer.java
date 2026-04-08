package com.ying.tech.community.service.article.consumer;

import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.article.message.TimelineRebuildMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 文章模块死信消费者
 *
 * <p>当主队列消费者重试3次全部失败后，消息路由至对应的死信队列（DLQ），由此消费者接管。
 *
 * <p>死信队列路由：
 * <ul>
 *   <li>article.publish.queue  → article.dlx → article.publish.dlq</li>
 *   <li>timeline.rebuild.queue → article.dlx → timeline.rebuild.dlq</li>
 * </ul>
 */
@Slf4j
@Component
public class ArticleDeadLetterConsumer {

    /**
     * 处理文章发布死信消息
     *
     * <p>触发场景：ArticlePublishConsumer 重试3次后仍失败（ZSet 写入或系统通知入库异常）
     */
    @RabbitListener(queues = "article.publish.dlq" , containerFactory = "autoAckListenerContainerFactory")
    public void handleArticlePublishDead(ArticlePublishMessage message,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.error("[DLQ][ArticlePublish] 消息进入死信队列，articleId: {}, authorId: {}, publishTime: {}",
                message.getArticleId(), message.getAuthorId(), message.getPublishTime());

        // TODO: 补偿处理，例如：
        //   1. 告警通知（钉钉/邮件）运维人工介入
        //   2. 写入补偿任务表，由定时任务定期重试
        //   3. 直接降级：同步写入 ZSet 并插入系统通知

        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "article.publish.timeline.dlq", containerFactory = "autoAckListenerContainerFactory")
    public void handleArticlePublishTimelineDead(ArticlePublishMessage message,
                                                 Channel channel,
                                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishTimeline] message in dlq, articleId: {}, authorId: {}, publishTime: {}",
            message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "article.publish.notify.dlq", containerFactory = "autoAckListenerContainerFactory")
    public void handleArticlePublishNotifyDead(ArticlePublishMessage message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishNotify] message in dlq, articleId: {}, authorId: {}, publishTime: {}",
            message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "article.publish.es.dlq", containerFactory = "autoAckListenerContainerFactory")
    public void handleArticlePublishEsDead(ArticlePublishMessage message,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishES] message in dlq, articleId: {}, authorId: {}, publishTime: {}",
            message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理时间轴重建死信消息
     *
     * <p>触发场景：TimelineRebuildConsumer 重试3次后仍失败（DB 查询或 Redis 写入异常）
     */
    @RabbitListener(queues = "timeline.rebuild.dlq")
    public void handleTimelineRebuildDead(TimelineRebuildMessage message,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.error("[DLQ][TimelineRebuild] 消息进入死信队列，rebuildTime: {}", message.getRebuildTime());

        // TODO: 补偿处理，例如：
        //   1. 告警通知运维人工介入
        //   2. 降级：直接触发同步重建逻辑（绕过 MQ）

        channel.basicAck(deliveryTag, false);
    }


    @RabbitListener(queues = "article.like.dlq")
    public void handleArticleLikeDead(Message message,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticleLike] 消息进入死信队列，message: {}", message);
        /// TODO: 补偿处理，例如：
        //   1. 告警通知运维人工介入
        //   2. 降级：直接触发同步重建逻辑（绕过 MQ）
        channel.basicAck(deliveryTag, false);
    }
}
