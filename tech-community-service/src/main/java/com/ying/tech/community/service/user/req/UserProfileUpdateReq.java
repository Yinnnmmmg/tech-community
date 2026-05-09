package com.ying.tech.community.service.user.req;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserProfileUpdateReq implements Serializable {
    private String username;
    private String photo;
    private String position;
    private String company;
    private String profile;
}
