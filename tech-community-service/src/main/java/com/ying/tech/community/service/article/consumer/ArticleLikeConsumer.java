package com.ying.tech.community.service.article.consumer;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.message.RedisLikeToDBMessage;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ArticleLikeConsumer {

    /** 幂等 Key 前缀，TTL 24 小时（消息在一天内不会重复投递） */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:article.like:";
    private static final long   IDEMPOTENT_TTL_HOURS  = 24;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserFootMapper userFootMapper;

    @RabbitListener(queues = "article.like.queue" ,containerFactory = "manualAckListenerContainerFactory")
    public void handleArticleLike(RedisLikeToDBMessage  message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId)
            throws IOException {
        log.info("[ArticleLike] 收到点赞消息, userId: {}, documentId: {}, documentUserId: {}, readStat: {}, likeStat: {}",
                message.getUserId(), message.getDocumentId(), message.getDocumentUserId(), message.getReadStat(), message.getLikeStat());
        // 幂等拦截
        if(messageId != null){
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + messageId;
            Integer likeStat = message.getLikeStat();
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, likeStat, IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if(Boolean.FALSE.equals(isNew)){
                log.warn("[ArticleLike] 幂等拦截，消息已处理，messageId: {}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
        }
        //用户和文章的点赞关系落库
        try{
            UpdateWrapper<UserFootDO> wrapper = new UpdateWrapper<UserFootDO>()
                    .set("like_stat", message.getLikeStat())
                    .eq("user_id", message.getUserId())
                    .eq("document_id", message.getDocumentId());
            int updateRow = userFootMapper.update(wrapper);
            if(updateRow == 0){
                UserFootDO userFootDO = new UserFootDO();
                BeanUtil.copyProperties(message, UserFootDO.class);
                userFootMapper.insert(userFootDO);
            }
            log.info("[ArticleLike] 点赞关系处理完成，messageId: {}", messageId);
        } catch (Exception e) {
            log.error("[ArticleLike] 点赞关系处理失败，messageId: {}, error: {}，等待 Spring Retry 重试",
                    messageId, e.getMessage(), e);
            // 3次全部失败后，RejectAndDontRequeueRecoverer 触发 DLX 路由至 article.like.dlq
            throw new RuntimeException(e);
        }
        // 业务全部成功后手动 ACK
        channel.basicAck(deliveryTag, false);

    }
}
