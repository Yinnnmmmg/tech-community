package com.ying.tech.community.service.user.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.user.entity.UserRelationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户关注关系 Mapper。
 * 负责 user_relation 表的增删改查。
 */
@Mapper
public interface UserRelationMapper extends BaseMapper<UserRelationDO> {
}
