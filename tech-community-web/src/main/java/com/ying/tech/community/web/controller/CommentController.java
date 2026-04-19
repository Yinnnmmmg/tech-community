package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.comment.req.CommentPublishReq;
import com.ying.tech.community.service.comment.service.CommentService;
import com.ying.tech.community.service.comment.vo.CommentArticlePageVO;
import com.ying.tech.community.service.comment.vo.CommentLikeVO;
import com.ying.tech.community.service.comment.vo.CommentReplyPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 发布评论接口
     * */
    @PostMapping("/publish")
    public Result<Long> commentPublish(@Validated @RequestBody CommentPublishReq req) {
        Long commentId = commentService.commentPublish(req);
        return Result.success(commentId);
    }

    /**
     * 查询文章评论列表接口
     * */
    @GetMapping("/article/{articleId}/list")
    public Result<CommentArticlePageVO> getArticleCommentList(@PathVariable Long articleId,
                                                              @RequestParam(defaultValue = "1") Integer page,
                                                              @RequestParam(defaultValue = "20") Integer size) {
        CommentArticlePageVO pageVO = commentService.getArticleCommentList(articleId, page, size);
        return Result.success(pageVO);
    }

    /**
     * 查询评论回复列表接口
     * */
    @GetMapping("/{commentId}/replies")
    public Result<CommentReplyPageVO> getCommentReplies(@PathVariable Long commentId,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "20") Integer size) {
        CommentReplyPageVO pageVO = commentService.getCommentReplies(commentId, page, size);
        return Result.success(pageVO);
    }

    /**
     * 评论点赞接口
     * */
    @PostMapping("/like")
    public Result<CommentLikeVO> likeComment(@RequestParam Long commentId) {
        CommentLikeVO commentLikeVO = commentService.likeComment(commentId);
        return Result.success(commentLikeVO);
    }

    /**
     * 删除评论接口
     * */
    @DeleteMapping("/{commentId}")
    public Result<Boolean> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return Result.success(Boolean.TRUE);
    }
}
