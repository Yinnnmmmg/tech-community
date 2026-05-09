package com.ying.tech.community.service.admin.service;

import com.ying.tech.community.service.admin.req.AdminCategorySaveReq;
import com.ying.tech.community.service.admin.vo.AdminCategoryVO;

import java.util.List;

public interface AdminCategoryService {
    List<AdminCategoryVO> getCategories();

    AdminCategoryVO createCategory(AdminCategorySaveReq req);

    AdminCategoryVO updateCategory(Long categoryId, AdminCategorySaveReq req);

    void updateCategoryStatus(Long categoryId, Integer status);
}
