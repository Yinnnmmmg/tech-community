package com.ying.tech.community.service.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ying.tech.community.service.entity.BaseDO;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("article")
public class ArticleDO extends BaseDO implements Serializable {
    private static final long serialVersionUID = 2L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer articleType;

    private String title;

    private String shortTitle;

    private String picture;

    private String summary;

    private Long categoryId;

    private Integer source;

    private String sourceUrl;

    private Integer officialStat;

    private Integer toppingStat;

    private Integer creamStat;

    private Integer status;

    @TableLogic
    private Integer deleted;
}
