package com.ying.tech.community.service.comment.service.impl;

import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.message.CommentPublishMessage;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.comment.req.CommentPublishReq;
import com.ying.tech.community.service.comment.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private Executor taskExecutor;

    /**
     * 发布评论
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long commentPublish(CommentPublishReq req) {
        //发布前的业务校验
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        //校验文章是否存在且允许评论
        validateArticle(req.getArticleId());
        //校验评论树结构是否合法
        validateCommentTree(req);

        //落库
        CommentDO commentDO = new CommentDO();
        commentDO.setArticleId(req.getArticleId());
        commentDO.setUserId(userId);
        commentDO.setParentCommentId(req.getParentCommentId());
        commentDO.setReplyToCommentId(req.getReplyToCommentId());
        commentDO.setReplyToUserId(req.getReplyToUserId());
        commentDO.setContent(req.getContent());
        commentDO.setStatus(PublishStatusConstants.PENDING);
        commentMapper.insert(commentDO);

        //异步发消息审核并完成副作用
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CommentPublishMessage message = new CommentPublishMessage();
                message.setCommentId(commentDO.getId());
                message.setAuthorId(userId);
                message.setPublishTime(System.currentTimeMillis());
                String commentId = UUID.randomUUID().toString();
                CorrelationData correlationData = new CorrelationData(commentId);
                taskExecutor.execute(() -> rabbitTemplate.convertAndSend(
                        "comment.publish.fanout", "", message, msg -> {
                            msg.getMessageProperties().setMessageId(commentId);
                            return msg;
                        }, correlationData));
            }
        });
        return commentDO.getId();
    }

    /**
     * 校验文章是否存在且允许评论
     */
    private void validateArticle(Long articleId) {
        ArticleDO articleDO = articleMapper.selectById(articleId);
        if (articleDO == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }
        if (!Objects.equals(articleDO.getStatus(), PublishStatusConstants.APPROVED)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 校验评论树结构是否合法
     */
    private void validateCommentTree(CommentPublishReq req) {
        Long parentCommentId = req.getParentCommentId();
        Long replyToCommentId = req.getReplyToCommentId();
        Long replyToUserId = req.getReplyToUserId();

        //一级评论：不允许携带回复目标
        if (parentCommentId == null) {
            if (replyToCommentId != null || replyToUserId != null) {
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            return;
        }

        //二级回复：回复目标必须完整
        if (replyToCommentId == null || replyToUserId == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        CommentDO parentComment = commentMapper.selectById(parentCommentId);
        if (!isSameArticleComment(parentComment, req.getArticleId())) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //二级回复的 parentCommentId 必须指向顶层评论
        if (parentComment.getParentCommentId() != null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        CommentDO replyToComment = commentMapper.selectById(replyToCommentId);
        if (!isSameArticleComment(replyToComment, req.getArticleId())) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //replyToUserId 必须和被回复评论作者一致
        if (!Objects.equals(replyToComment.getUserId(), replyToUserId)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //被回复评论必须属于当前顶层评论树，禁止构造三级评论
        if (!belongsToCurrentTopComment(replyToComment, parentCommentId)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 校验评论是否存在且属于当前文章
     */
    private boolean isSameArticleComment(CommentDO commentDO, Long articleId) {
        return commentDO != null && Objects.equals(commentDO.getArticleId(), articleId);
    }

    /**
     * 校验被回复评论是否属于当前顶层评论树
     */
    private boolean belongsToCurrentTopComment(CommentDO replyToComment, Long parentCommentId) {
        if (replyToComment.getParentCommentId() == null) {
            return Objects.equals(replyToComment.getId(), parentCommentId);
        }
        return Objects.equals(replyToComment.getParentCommentId(), parentCommentId);
    }
}
