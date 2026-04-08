package com.ying.tech.community.service.article.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.service.article.entity.es.ArticleDocument;
import com.ying.tech.community.service.article.vo.ArticleSearchHighlightVO;

import java.util.List;

public interface ArticleSearchService {

   //全文高亮搜索
    Page<ArticleSearchHighlightVO> searchWithHighlight(String keyword, Integer page, Integer size);

}
