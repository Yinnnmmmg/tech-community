package com.ying.tech.community.service.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 关注列表项返回对象。
 * 用于关注列表和粉丝列表的统一展示结构。
 */
@Data
@Builder
public class UserFollowListItemVO {
    /**
     * 列表项对应的用户 ID。
     */
    private Long userId;

    /**
     * 展示名称。
     */
    private String username;

    /**
     * 头像地址。
     */
    private String photo;

    /**
     * 职位信息。
     */
    private String position;

    /**
     * 公司信息。
     */
    private String company;

    /**
     * 个人简介。
     */
    private String profile;

    /**
     * 当前登录用户是否已关注列表中的这个人。
     */
    private Boolean followed;
}
