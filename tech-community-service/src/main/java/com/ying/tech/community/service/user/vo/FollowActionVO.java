package com.ying.tech.community.service.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 关注动作返回对象。
 * 用于返回某个目标用户的最终关注状态。
 */
@Data
@Builder
public class FollowActionVO {
    /**
     * 目标用户 ID。
     */
    private Long targetUserId;

    /**
     * 当前用户是否已关注目标用户。
     */
    private Boolean followed;
}
