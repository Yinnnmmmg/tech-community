package com.ying.tech.community.service.user.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserProfileVO implements Serializable {
    private Long userId;
    private String username;
    private Integer userRole;
    private String photo;
    private String position;
    private String company;
    private String profile;
    private Long articleCount;
    private Long followCount;
    private Long fanCount;
    private Long collectionCount;
    private Long likeCount;
    private Boolean followed;
    private Boolean self;
    private String createTime;
}
