package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;


    /**
     * 根据文章id获取文章详情
     *
     * */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        //先把文章id转为文章详情表的id
        Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
        ArticleDetailDO articleDetailDO = articleDetailMapper.selectById(articleDetailId);
        if (articleDetailDO == null) {
            log.warn("文章详情不存在，articleId: {}", articleId);
            return null;
        }
        ArticleDetailVO articleDetailVO = new ArticleDetailVO();
        BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
        return articleDetailVO;
    }
}
