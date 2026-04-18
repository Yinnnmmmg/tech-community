package com.ying.tech.community.service.comment.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<CommentDO> {
}
