package com.ying.tech.community.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("user")
public class UserDO extends BaseDO implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String thirdAccountId;

    private String username;

    private String phone;

    private String password;

    private Integer loginType;

    private Integer userRole;

    @TableLogic
    private Integer deleted;
}
