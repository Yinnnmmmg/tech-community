package com.ying.tech.community.service.admin.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.admin.vo.AdminCommentListItemVO;

public interface AdminCommentService {
    PageResult<AdminCommentListItemVO> getComments(Integer status,
                                                   Long articleId,
                                                   String keyword,
                                                   Integer page,
                                                   Integer size);

    void approveComment(Long commentId);

    void rejectComment(Long commentId, String reason);

    void deleteComment(Long commentId);
}
