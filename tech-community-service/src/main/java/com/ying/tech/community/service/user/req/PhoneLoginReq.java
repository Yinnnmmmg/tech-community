package com.ying.tech.community.service.user.req;

import lombok.Data;
import java.io.Serializable;

@Data
public class PhoneLoginReq implements Serializable {
    private String phone;
    private String password;
    private String smsCode;
}
