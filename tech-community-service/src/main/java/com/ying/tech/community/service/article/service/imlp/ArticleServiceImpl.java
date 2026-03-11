package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;

    /**
     * 发布文章
     * @param articlePostReq
     * @return Long 文章id
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long publishArticle(ArticlePostReq articlePostReq) {
        // 1、获取当前用户的 id
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        log.info("发布文章，当前用户 ID: {}", userId);

        // 2、插入文章表
        ArticleDO article = new ArticleDO();
        BeanUtil.copyProperties(articlePostReq, article);
        article.setUserId(userId);
        articleMapper.insert(article);
        log.info("文章表插入成功，文章 ID: {}", article.getId());

        // 3、插入文章细节表
        ArticleDetailDO articleDetail = new ArticleDetailDO();
        BeanUtil.copyProperties(articlePostReq, articleDetail);
        articleDetail.setArticleId(article.getId());
        articleDetailMapper.insert(articleDetail);
        log.info("文章详情表插入成功");

        return article.getId();
    }


    /**
     * 查询文章列表
     * GET /article/list
     * @param pageNum
     * @param pageSize@param pageNum 页码
     * @return PageResult<ArticleListVO>
     **/
    @Override
    public PageResult<ArticleListVO> getArticleList(Integer pageNum, Integer pageSize) {
        log.info("查询文章列表，pageNum: {}, pageSize: {}", pageNum, pageSize);
        // 构建分页参数
        Page<ArticleDO> pageParam = new Page<>(pageNum, pageSize);
        // 查询文章列表
        Page<ArticleDO> articlePage = articleMapper.selectPage(pageParam, null);
        // 从分页结果中获取文章列表,并转换为VO
        //TODO要把VO的属性补充完整
        List<ArticleListVO> articleList = BeanUtil.copyToList(articlePage.getRecords(), ArticleListVO.class);
        log.info("文章列表查询成功，文章列表: {}", articleList);
        return new PageResult<ArticleListVO>(articlePage.getTotal(), articleList);
    }
}