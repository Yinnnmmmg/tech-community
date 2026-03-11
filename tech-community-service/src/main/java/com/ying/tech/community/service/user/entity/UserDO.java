package com.ying.tech.community.service.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user") // 对应数据库表名
public class UserDO extends BaseDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO) // 对应主键自增
    private Long id;

    private String thirdAccountId;

    private String username;

    private String password;

    private Integer loginType;

    // 逻辑删除注解 (0未删, 1已删)
    @TableLogic
    private Integer deleted;
}
