package com.ying.tech.community.service.comment.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 评论列表项视图对象
 * 用于在评论列表中展示单条评论的完整信息
 */
@Data
public class CommentListItemVO implements Serializable {
    
    /**
     * 评论ID
     */
    private Long commentId;
    
    /**
     * 关联的文章ID
     */
    private Long articleId;
    
    /**
     * 评论用户ID
     */
    private Long userId;
    
    /**
     * 评论用户名
     */
    private String username;

    /**
     * 评论用户头像
     */
    private String photo;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 评论状态（0-待审核，1-已通过，2-已拒绝等）
     */
    private Integer status;
    
    /**
     * 拒绝原因（当状态为已拒绝时填写）
     */
    private String rejectReason;
    
    /**
     * 点赞数量
     */
    private Long likeCount;
    
    /**
     * 点赞统计（当前用户是否点赞）
     */
    private Long likeStat;
    
    /**
     * 回复数量
     */
    private Long replyCount;
    
    /**
     * 回复的目标用户ID（如果是回复评论）
     */
    private Long replyToUserId;
    
    /**
     * 回复的目标用户名（如果是回复评论）
     */
    private String replyToUsername;
    
    /**
     * 评论创建时间
     */
    private String createTime;
    
    /**
     * 当前用户是否有删除权限
     */
    private Boolean canDelete;
}
