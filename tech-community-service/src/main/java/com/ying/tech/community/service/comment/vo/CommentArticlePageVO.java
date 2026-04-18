package com.ying.tech.community.service.comment.vo;

import com.ying.tech.community.core.common.PageResult;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CommentArticlePageVO implements Serializable {
    private PageResult<CommentListItemVO> publicPage;
    private List<CommentListItemVO> mine;
}