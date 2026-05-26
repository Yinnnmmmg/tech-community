package com.ying.tech.community.service.user.req;

import lombok.Data;
import java.io.Serializable;

@Data
public class ChangePasswordReq implements Serializable {
    private String oldPassword;
    private String newPassword;
}
