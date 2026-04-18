package com.ying.tech.community.service.notifyMsg.consumer;

import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.service.comment.message.CommentPublishMessage;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommentPublishFailConsumer {

    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    @RabbitListener(queues = "comment.publish.review.fail.queue", containerFactory = "autoAckListenerContainerFactory")
    public void handle(CommentPublishMessage message) {
        log.error("[CommentPublishFail] handle failed, commentId={}", message.getCommentId());
        // 持久化系统消息给用户，提示评论发布失败
        String reason = message.getReason();
        NotifyMsgDO notifyMsgDO = NotifyMsgDO.builder()
                .relatedId(message.getCommentId())
                .notifyUserId(message.getAuthorId())
                .operateUserId(message.getAuthorId())
                .msg("评论发布失败，失败原因：" + reason)
                .type(NotifyMsgConstants.Type.COMMENT)
                .build();
        notifyMsgMapper.insert(notifyMsgDO);
    }
}
