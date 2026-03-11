package com.ying.tech.community.service.article.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleDetailMapper extends BaseMapper<ArticleDetailDO> {
    @Select("select id from article_detail where article_id = #{articleId}")
    Long getArticleDetailIdById(Long articleId);
}
