package com.ying.tech.community.service.notifyMsg.consumer;

import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.article.message.ArticlePublishMessage;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PublishFailConsumer {
    @Autowired
    private NotifyMsgMapper notifyMsgMapper;
    @RabbitListener(queues = "notify.publish.fail.queue" , containerFactory = "autoAckListenerContainerFactory")
    public void handle(ArticlePublishMessage message) {
        //给发送失败的消息发送者发送消息
        log.info("[PublishFailConsumer] receive, message={}", message);
        if(message == null){
            log.warn("[PublishFailConsumer] message is null");
            return;
        }
        Long authorId = message.getAuthorId();
        NotifyMsgDO notifyMsg = NotifyMsgDO.builder()
                .relatedId(message.getArticleId())           // 关联的文章ID
                .notifyUserId(authorId)                     // 通知的用户（文章作者）
                .operateUserId(authorId)                    // 操作的用户（文章作者）
                .msg("您的文章审核未通过")                        // 通知消息内容
                .type(NotifyMsgConstants.Type.SYSTEM)        // 通知类型：系统通知
                .state(NotifyMsgConstants.State.UNREAD)      // 通知状态：未读
                .build();
        // 将通知保存到数据库
        notifyMsgMapper.insert(notifyMsg);
        log.info("[ArticlePublishNotify] notify insert success, articleId={}", message.getArticleId());
    }
}
