package com.ying.tech.community.service.admin.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminCategoryVO implements Serializable {
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    private Long articleCount;
    private String createTime;
    private String updateTime;
}
