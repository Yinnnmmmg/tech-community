package com.ying.tech.community.service.notifyMsg.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.message.CommentPublishMessage;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommentPublishSuccessConsumer {
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    @RabbitListener(queues = "comment.publish.review.success.queue", containerFactory = "autoAckListenerContainerFactory")
    public void handle(CommentPublishMessage message) {
        log.info("CommentPublishSuccessConsumer: {}", message);
        //发送系统消息通知文章作者或者原评论作者
        Long commentId = message.getCommentId();
        QueryWrapper<CommentDO> queryWrapper = new QueryWrapper<CommentDO>()
                .eq("id", commentId);
        CommentDO commentDO = commentMapper.selectOne(queryWrapper);
        Long parentCommentId = commentDO.getParentCommentId();
        //通知文章作者
        Long authorId = articleMapper.selectById(commentDO.getArticleId()).getUserId();
        if(parentCommentId == null){
            NotifyMsgDO notifyMsgDO = NotifyMsgDO.builder()
                    .relatedId(commentDO.getArticleId())
                    .notifyUserId(authorId)
                    .operateUserId(message.getAuthorId())
                    .msg("你的文章有新评论")
                    .type(1)
                    .state(0)
                    .build();
            notifyMsgMapper.insert(notifyMsgDO);
        }
        //通知原评论作者
        if(parentCommentId != null){
            NotifyMsgDO notifyMsgDO = NotifyMsgDO.builder()
                    .relatedId(commentDO.getReplyToCommentId())
                    .notifyUserId(commentDO.getReplyToUserId())
                    .operateUserId(message.getAuthorId())
                    .msg("你的评论有新回复")
                    .type(2)
                    .state(0)
                    .build();
            notifyMsgMapper.insert(notifyMsgDO);
        }
    }

}
