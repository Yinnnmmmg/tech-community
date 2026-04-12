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
 *   <li>timeline.rebuild.queue → article.dlx → timeline.rebuild.dlq</li>
 * </ul>
 */
@Slf4j
@Component
public class ArticleDeadLetterConsumer {
    @RabbitListener(queues = "article.publish.timeline.dlq")
    public void handleArticlePublishTimelineDead(ArticlePublishMessage message,
                                                 Channel channel,
                                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishTimeline] message in dlq, articleId: {}, authorId: {}, publishTime: {}",
            message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "article.publish.notify.dlq")
    public void handleArticlePublishNotifyDead(ArticlePublishMessage message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishNotify] message in dlq, articleId: {}, authorId: {}, publishTime: {}",
            message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "article.publish.es.dlq")
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

    @RabbitListener(queues = "article.publish.review.dlq")
    public void handleArticlePublishReviewDead(Message message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishReview] 消息进入死信队列，message: {}", message);
        /// TODO: 补偿处理，例如：
        //   1. 告警通知运维人工介入
        //   2. 降级：直接触发同步重建逻辑（绕过 MQ）
            }
}
