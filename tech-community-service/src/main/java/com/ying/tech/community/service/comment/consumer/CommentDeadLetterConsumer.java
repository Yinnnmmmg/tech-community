package com.ying.tech.community.service.comment.consumer;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 评论相关死信消费者。
 *
 * <p>当评论主队列的消息经过多次重试仍然失败时，会被路由到对应死信队列，
 * 由该消费者统一记录日志，便于后续人工排查和补偿处理。
 */
@Slf4j
@Component
public class CommentDeadLetterConsumer {
    /**
     * 处理评论发布审核死信消息。
     */
    @RabbitListener(queues = "comment.publish.dlq")
    public void handleCommentPublishDead(Message message,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        log.error("[DLQ][CommentPublishReview] message={}", message);
        channel.basicAck(deliveryTag, false);
    }
}
