package com.ying.tech.community.service.ai.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.ai.entity.PublishAiResult;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.message.CommentPublishMessage;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.comment.service.CommentReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.ying.tech.community.core.constants.AiConstants.COMMENT_REVIEW_SYSTEM_PROMPT;

/**
 * 评论审核消费者
 * */
@Slf4j
@Component
public class ReviewCommentConsumer {

    // 幂等 Redis Key 前缀
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:review-comment:";
    // 幂等 Key 过期时间（小时）
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    /** DeepSeek大模型客户端：用于调用AI模型进行评论内容安全审核 */
    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;
    /** RedisTemplate：用于幂等性检查，基于messageId防止重复消费 */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CommentReviewService commentReviewService;

    /**
     * 处理评论的审核结果
     * */
    @RabbitListener(queues = "comment.publish.review.queue", containerFactory = "manualAckListenerContainerFactory")
    public void handle(CommentPublishMessage  message,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                       @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) throws IOException {
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

        try{
            //拿到评论详情
            QueryWrapper<CommentDO> contentWrapper = new QueryWrapper<CommentDO>()
                    .select("content").eq("id", message.getCommentId());
            String content = commentMapper.selectOne(contentWrapper).getContent();
            String userPrompt = "以下是评论的内容：" + content;
            //调用大模型审核
            PublishAiResult reviewResult = chatClient.prompt()
                    .system(COMMENT_REVIEW_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call().entity(PublishAiResult.class);
            //没过，把状态改为REJECTED，发送系统消息通知用户，并记录拒绝理由
            if(!reviewResult.isSafe()){
                String reason = reviewResult.reason();
                commentReviewService.rejectComment(message.getCommentId(), reason);
                //发送系统消息通知用户，并记录拒绝理由
                message.setReason(reason);
                rabbitTemplate.convertAndSend("comment.publish.direct","comment.publish.fail",message);
                //手动确认
                channel.basicAck(deliveryTag, false);
                return;
            }
            //过了，把状态改为APPROVED，文章的评论数加一（父评论的回复数加一），user_foot的comment_stat 标记为 1
            // 发送系统消息通知文章作者或者原评论作者
            if(reviewResult.isSafe()){
                commentReviewService.approveComment(message.getCommentId(), message.getAuthorId());
                //发送系统消息通知文章作者或者原评论作者
                rabbitTemplate.convertAndSend("comment.publish.direct","comment.publish.success",message);
                channel.basicAck(deliveryTag, false);
            }

        } catch(Exception e){
            log.error("[ReviewAndSummary] handle failed, articleId={}, error={}", message.getCommentId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
