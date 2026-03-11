package com.ying.tech.community.service.user.req;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserSaveReq implements Serializable {
    private String username;
    private String password;
    // 注册时只需要这两个，千万别把 role 放进来
}