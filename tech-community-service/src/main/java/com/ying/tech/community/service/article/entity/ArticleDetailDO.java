package com.ying.tech.community.service.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("article_detail")
public class ArticleDetailDO extends BaseDO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long articleId;
    
    private Integer version;
    
    private String content;
    
    @TableLogic
    private Integer deleted;
}
