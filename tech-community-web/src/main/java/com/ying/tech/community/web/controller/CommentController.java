package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.comment.req.CommentPublishReq;
import com.ying.tech.community.service.comment.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public Result<Long> commentPublish(@Validated @RequestBody CommentPublishReq  req){
        Long commentId = commentService.commentPublish(req);
        return Result.success(commentId);
    }
}
