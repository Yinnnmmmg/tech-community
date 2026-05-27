package com.ying.tech.community.service.user.req;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserSaveReq implements Serializable {
    private String username;
    private String phone;
    private String password;
    private String smsCode;
}
