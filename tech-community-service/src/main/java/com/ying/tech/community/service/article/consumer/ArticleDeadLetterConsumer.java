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
 * 文章相关死信消费者。
 *
 * <p>当主队列消息经过多次重试仍然失败时，消息会被路由到对应死信队列，由该消费者统一记录日志，
 * 便于后续人工排查和补偿处理。
 */
@Slf4j
@Component
public class ArticleDeadLetterConsumer {
    /**
     * 处理文章发布时间轴广播死信消息。
     */
    @RabbitListener(queues = "article.publish.timeline.dlq")
    public void handleArticlePublishTimelineDead(ArticlePublishMessage message,
                                                 Channel channel,
                                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishTimeline] articleId={}, authorId={}, publishTime={}",
                message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章发布通知死信消息。
     */
    @RabbitListener(queues = "article.publish.notify.dlq")
    public void handleArticlePublishNotifyDead(ArticlePublishMessage message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishNotify] articleId={}, authorId={}, publishTime={}",
                message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }

    // [ES-OLD] 处理文章发布后 ES 同步死信消息 —— 已改为 MySQL FULLTEXT，不再需要
    /*
    @RabbitListener(queues = "article.publish.es.dlq")
    public void handleArticlePublishEsDead(ArticlePublishMessage message,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishES] articleId={}, authorId={}, publishTime={}",
                message.getArticleId(), message.getAuthorId(), message.getPublishTime());
        channel.basicAck(deliveryTag, false);
    }
    */

    /**
     * 处理时间轴重建死信消息。
     *
     * <p>触发场景通常为时间轴重建过程中数据库查询或 Redis 写入失败。
     */
    @RabbitListener(queues = "timeline.rebuild.dlq")
    public void handleTimelineRebuildDead(TimelineRebuildMessage message,
                                          Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][TimelineRebuild] rebuildTime={}", message.getRebuildTime());
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章点赞落库死信消息。
     */
    @RabbitListener(queues = "article.like.dlq")
    public void handleArticleLikeDead(Message message,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticleLike] message={}", message);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章收藏落库死信消息。
     */
    @RabbitListener(queues = "article.collect.dlq")
    public void handleArticleCollectDead(Message message,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticleCollect] message={}", message);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章点赞通知死信消息。
     */
    @RabbitListener(queues = "article.like.notify.dlq")
    public void handleArticleLikeNotifyDead(Message message,
                                            Channel channel,
                                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticleLikeNotify] message={}", message);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章收藏通知死信消息。
     */
    @RabbitListener(queues = "article.collect.notify.dlq")
    public void handleArticleCollectNotifyDead(Message message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticleCollectNotify] message={}", message);
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 处理文章发布审核死信消息。
     */
    @RabbitListener(queues = "article.publish.review.dlq")
    public void handleArticlePublishReviewDead(Message message,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][ArticlePublishReview] message={}", message);
        channel.basicAck(deliveryTag, false);
    }
}
