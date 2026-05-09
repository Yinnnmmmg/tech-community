package com.ying.tech.community.service.admin.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminUserListItemVO implements Serializable {
    private Long userId;
    private String username;
    private Integer userRole;
    private String photo;
    private String position;
    private String company;
    private String profile;
    private String createTime;
}
