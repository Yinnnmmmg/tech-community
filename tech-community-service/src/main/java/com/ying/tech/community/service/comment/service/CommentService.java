package com.ying.tech.community.service.comment.service;

import com.ying.tech.community.service.comment.req.CommentPublishReq;
import com.ying.tech.community.service.comment.vo.CommentArticlePageVO;
import com.ying.tech.community.service.comment.vo.CommentLikeVO;
import com.ying.tech.community.service.comment.vo.CommentReplyPageVO;

public interface CommentService {
    Long commentPublish(CommentPublishReq req);

    CommentArticlePageVO getArticleCommentList(Long articleId, Integer page, Integer size);

    CommentReplyPageVO getCommentReplies(Long commentId, Integer page, Integer size);

    CommentLikeVO likeComment(Long commentId);

    void deleteComment(Long commentId);
}
