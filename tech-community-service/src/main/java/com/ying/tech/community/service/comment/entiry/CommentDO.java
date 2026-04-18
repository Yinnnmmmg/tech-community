package com.ying.tech.community.service.comment.entiry;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("comment")
public class CommentDO extends BaseDO implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    private Long rootCommentId;

    private Long parentCommentId;

    private Long replyToCommentId;

    private Long replyToUserId;

    private String content;

    private Integer status;

    private String rejectReason;

    private Integer likeCount;

    private Integer replyCount;

    @TableLogic
    private Integer deleted;
}
