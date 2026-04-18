package com.ying.tech.community.service.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.comment.service.CommentReviewService;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ying.tech.community.core.constants.PublishStatusConstants.APPROVED;
import static com.ying.tech.community.core.constants.PublishStatusConstants.REJECTED;

@Service
public class CommentReviewServiceImpl implements CommentReviewService {
    private static final int DOCUMENT_TYPE_ARTICLE = 1;

    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private UserFootMapper userFootMapper;

    /**
     * 驳回评论：状态和驳回原因一起事务提交
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rejectComment(Long commentId, String reason) {
        commentMapper.update(null, new UpdateWrapper<CommentDO>()
                .eq("id", commentId)
                .set("status", REJECTED)
                .set("reject_reason", reason));
    }

    /**
     * 通过评论：状态、聚合计数和用户足迹一起事务提交
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void approveComment(Long commentId, Long authorId) {
        CommentDO commentDO = commentMapper.selectById(commentId);
        if (commentDO == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        ArticleDO articleDO = articleMapper.selectById(commentDO.getArticleId());
        if (articleDO == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }

        //评论状态改为通过
        commentMapper.update(null, new UpdateWrapper<CommentDO>()
                .eq("id", commentId)
                .set("status", APPROVED));

        //文章评论数 +1
        articleMapper.update(null, new UpdateWrapper<ArticleDO>()
                .eq("id", articleDO.getId())
                .setSql("comment_count = comment_count + 1"));

        //如果本条评论是回复，父评论回复数 +1
        if (commentDO.getParentCommentId() != null) {
            commentMapper.update(null, new UpdateWrapper<CommentDO>()
                    .eq("id", commentDO.getParentCommentId())
                    .setSql("reply_count = reply_count + 1"));
        }

        //文章维度的 user_foot.comment_stat 标记为 1
        syncCommentFoot(authorId, articleDO);
    }

    /**
     * 同步文章维度的评论足迹
     */
    private void syncCommentFoot(Long authorId, ArticleDO articleDO) {
        UpdateWrapper<UserFootDO> wrapper = new UpdateWrapper<UserFootDO>()
                .set("comment_stat", 1)
                .eq("user_id", authorId)
                .eq("document_id", articleDO.getId())
                .eq("document_type", DOCUMENT_TYPE_ARTICLE);
        int updateRow = userFootMapper.update(null, wrapper);
        if (updateRow > 0) {
            return;
        }

        //更新不到通常说明这篇文章维度的足迹还不存在，需要补建一条记录
        UserFootDO userFootDO = new UserFootDO();
        userFootDO.setUserId(authorId);
        userFootDO.setDocumentId(articleDO.getId());
        userFootDO.setDocumentType(DOCUMENT_TYPE_ARTICLE);
        userFootDO.setDocumentUserId(articleDO.getUserId());
        userFootDO.setCollectionStat(0);
        userFootDO.setReadStat(0);
        userFootDO.setCommentStat(1);
        userFootDO.setLikeStat(0);
        try {
            userFootMapper.insert(userFootDO);
        } catch (DuplicateKeyException duplicateKeyException) {
            //并发场景下可能已经被别的流程先插入，回退为 update 即可
            int retryUpdateRow = userFootMapper.update(null, wrapper);
            if (retryUpdateRow == 0) {
                throw duplicateKeyException;
            }
        }
    }
}
