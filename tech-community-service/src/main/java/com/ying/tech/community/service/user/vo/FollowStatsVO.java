package com.ying.tech.community.service.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 关注统计返回对象。
 * 汇总用户的关注数、粉丝数以及当前登录用户是否已关注该用户。
 */
@Data
@Builder
public class FollowStatsVO {
    /**
     * 该用户关注了多少人。
     */
    private Long followCount;

    /**
     * 该用户拥有多少粉丝。
     */
    private Long fanCount;

    /**
     * 当前登录用户是否已关注该用户。
     */
    private Boolean followed;
}
