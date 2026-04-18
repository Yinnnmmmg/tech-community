package com.ying.tech.community.service.comment.service;

public interface CommentReviewService {
    void rejectComment(Long commentId, String reason);

    void approveComment(Long commentId, Long authorId);
}
