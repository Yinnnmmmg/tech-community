package com.ying.tech.community.service.user.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserCurrentVO implements Serializable {
    private Long id;
    private String username;
    private Integer userRole;
    private String photo;
    private String position;
    private String company;
    private String profile;
}
