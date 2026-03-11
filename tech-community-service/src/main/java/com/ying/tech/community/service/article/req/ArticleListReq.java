package com.ying.tech.community.service.article.req;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleListReq implements Serializable {
    private Integer pageNum;
    private Integer pageSize;
}
