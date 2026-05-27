package com.ying.tech.community.service.user.req;

import lombok.Data;
import java.io.Serializable;

@Data
public class SendSmsReq implements Serializable {
    private String phone;
}
