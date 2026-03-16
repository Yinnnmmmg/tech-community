package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 根据文章id获取文章详情
     *
     * */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        //先把文章id转为文章详情表的id
        Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
        if(articleDetailId == null){
            log.warn("文章不存在，articleId: {}", articleId);
            return null;
        }
        //拼接redis的key
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleDetailId;
        ArticleDetailDO articleDetailDO = new ArticleDetailDO();
        //查redis
        articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
        //存在，返回
        if(articleDetailDO != null){
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
            return articleDetailVO;
        }

        //不存在，查数据库
        articleDetailDO = articleDetailMapper.selectById(articleDetailId);
        if (articleDetailDO == null) {
            log.warn("文章详情不存在，articleId: {}", articleId);
            return null;
        }
        ArticleDetailVO articleDetailVO = new ArticleDetailVO();
        BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
        //重建缓存
        redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO);
        return articleDetailVO;
    }
}
