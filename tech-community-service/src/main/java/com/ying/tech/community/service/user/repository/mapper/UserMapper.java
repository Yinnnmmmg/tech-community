package com.ying.tech.community.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.user.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper 接口
 * 继承 BaseMapper 后，你就直接拥有了增删改查（CRUD）的能力
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}