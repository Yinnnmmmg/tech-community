package com.ying.tech.community.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
@TableName("user_foot")
public class UserFootDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 文档 ID
     */
    private Long articleId;

    /**
     * 文档类型
     */
    private Integer articleType;

    /**
     * 文章作者的ID
     */
    private Long articleUserId;

    /**
     * 收藏状态 (0 未收藏，1 已收藏)
     */
    private Integer collectionStat;

    /**
     * 阅读状态 (0 未读，1 已读)
     */
    private Integer readStat;

    /**
     * 评论状态 (0 未评论，1 已评论)
     */
    private Integer commentStat;

    /**
     * 点赞状态 (0 未点赞，1 已点赞)
     */
    private Integer likeStat;
}
