package com.ying.tech.community.service.article.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleCategoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Integer sort;
}
