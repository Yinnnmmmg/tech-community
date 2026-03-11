package com.ying.tech.community.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("user_info")
public class UserInfoDO extends BaseDO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String username;
    
    private String photo;
    
    private String position;
    
    private String company;
    
    private String profile;
    
    private Integer userRole;
    
    private String extend;
    
    private String ip;
    
    @TableLogic
    private Integer deleted;
}
