package com.ying.tech.community.service.comment.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.comment.vo.CommentListItemVO;

import java.util.Collection;
import java.util.function.Supplier;

public interface CommentCacheService {
    PageResult<CommentListItemVO> getArticlePublicPage(Long articleId, Integer page, Integer size,
                                                       Supplier<PageResult<CommentListItemVO>> loader);

    PageResult<CommentListItemVO> getReplyPublicPage(Long parentCommentId, Integer page, Integer size,
                                                     Supplier<PageResult<CommentListItemVO>> loader);

    void bumpArticleListVersion(Long articleId);

    void bumpReplyListVersion(Long parentCommentId);

    void clearArticleSummaryCache(Long articleId);

    void clearCommentLikeCache(Collection<Long> commentIds);
}
