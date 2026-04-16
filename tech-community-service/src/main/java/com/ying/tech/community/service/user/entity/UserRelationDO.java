package com.ying.tech.community.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户关注关系实体。
 * 一条记录表示“某个关注者”对“某个被关注用户”的当前关注状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("user_relation")
public class UserRelationDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 被关注的用户 ID。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 发起关注动作的用户 ID。
     */
    @TableField("follow_user_id")
    private Long followUserId;

    /**
     * 关注状态。
     * 约定 1 为已关注，2 为取消关注。
     */
    @TableField("follow_state")
    private Integer followState;
}
