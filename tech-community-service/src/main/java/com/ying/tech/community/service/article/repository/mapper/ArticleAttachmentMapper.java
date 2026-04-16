package com.ying.tech.community.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.article.entity.ArticleAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章附件 Mapper。
 * 提供 article_attachment 表的基础增删改查能力。
 */
@Mapper
public interface ArticleAttachmentMapper extends BaseMapper<ArticleAttachmentDO> {
}
