package com.ying.tech.community.service.comment.service;

import com.ying.tech.community.service.comment.req.CommentPublishReq;

public interface CommentService {
    Long commentPublish(CommentPublishReq req);
}
