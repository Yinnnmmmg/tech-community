package com.ying.tech.community.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.user.entity.UserInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户资料 Mapper。
 * 提供 user_info 表的基础访问能力。
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfoDO> {
}
