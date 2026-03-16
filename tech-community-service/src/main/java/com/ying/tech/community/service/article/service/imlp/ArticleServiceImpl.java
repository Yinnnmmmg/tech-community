package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.req.ArticlePostReq;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleLikeVO;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private UserFootMapper userFootMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发布文章
     * @param articlePostReq
     * @return Long 文章id
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long publishArticle(ArticlePostReq articlePostReq) {
        // 获取当前用户的 id
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        log.info("发布文章，当前用户 ID: {}", userId);

        // 插入文章表到数据库
        ArticleDO article = new ArticleDO();
        BeanUtil.copyProperties(articlePostReq, article);
        article.setUserId(userId);
        articleMapper.insert(article);
        log.info("文章表插入数据库成功，文章 ID: {}", article.getId());
        //添加到redis
        //用Zset来存，为了按时间做排序
        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        redisTemplate.opsForZSet().add(articleListKey, article.getId().toString(), System.currentTimeMillis());
        //正常存，方便根据id来查
        String articleKey = RedisConstants.TECH_COMMUNITY_ARTICLE + article.getId();
        redisTemplate.opsForValue().set(articleKey,article);
        log.info("文章表插入redis成功，文章 ID: {}", article.getId());

        // 插入文章细节表
        ArticleDetailDO articleDetail = new ArticleDetailDO();
        BeanUtil.copyProperties(articlePostReq, articleDetail);
        articleDetail.setArticleId(article.getId());
        articleDetailMapper.insert(articleDetail);
        log.info("文章详情表插入成功");
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleDetail.getId();
        //添加到redis
        redisTemplate.opsForValue().set(articleDetailKey,articleDetail);
        log.info("文章详情表插入redis成功，文章 ID: {}", article.getId());

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
        int start = (pageNum - 1) * pageSize;
        int end = start + pageSize - 1;
        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        // 1. 查 ZSet 获取当前页的文章IDs
        Set<String> idSet = redisTemplate.opsForZSet().reverseRange(articleListKey, start, end);

        // 降级：ZSet 为空，直接查库返回当前页  场景： Redis 宕机重启、Zset 被意外删除或人为清空。
        if (idSet == null || idSet.isEmpty()) {
            //TODO 目前只查数据库返回，后期改造成：先返回当前页，然后用mq异步发送消息去全量回表
            Page<ArticleDO> page = new Page<>(pageNum, pageSize);
            Page<ArticleDO> articlePage = articleMapper.selectPage(page, null);
            List<ArticleListVO> articleVOs = articlePage.getRecords().stream()
                    .map(doObj -> BeanUtil.copyProperties(doObj, ArticleListVO.class))
                    .collect(Collectors.toList());
            return new PageResult<>(articlePage.getTotal(), articleVOs);
        }

        // 将 Set 转为 List 以保持 ZSet 原有的排序顺序
        List<String> orderedIds = new ArrayList<>(idSet);

        // 2. 批量获取文章详情
        // 把ids组装成keys，方便查redis
        List<String> keys = orderedIds.stream()
                .map(id -> RedisConstants.TECH_COMMUNITY_ARTICLE + id)
                .collect(Collectors.toList());
        List<Object> cachedArticles = redisTemplate.opsForValue().multiGet(keys);

        // 3. 找出缓存缺失的 文章ID 和对应的索引位置
        List<String> missingIds = new ArrayList<>();
        // multiGet 的结果列表大小和顺序与 keys 完全一致
        for (int i = 0; i < cachedArticles.size(); i++) {
            if (cachedArticles.get(i) == null) {
                missingIds.add(orderedIds.get(i));
            }
        }

        // 4. 回表查库，并回写 Redis
        Map<String, ArticleDO> missingArticlesMap = new HashMap<>();
        if (!missingIds.isEmpty()) {
            log.info("Redis 缓存缺失，批量回表查库，missingIds: {}", missingIds);
            List<ArticleDO> dbArticles = articleMapper.selectBatchIds(missingIds);

            Map<String, Object> redisBatchData = new HashMap<>();
            for (ArticleDO article : dbArticles) {
                String idStr = article.getId().toString();
                missingArticlesMap.put(idStr, article); // 存入本地 Map 备用
                redisBatchData.put(RedisConstants.TECH_COMMUNITY_ARTICLE + idStr, article);
            }

            // 性能优化：使用 multiSet 一次性批量写入缓存
            if (!redisBatchData.isEmpty()) {
                redisTemplate.opsForValue().multiSet(redisBatchData);
            }
        }

        // 5.按照 ZSet 原有的顺序，重新组装最终列表
        List<ArticleListVO> finalVOs = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            String articleId = orderedIds.get(i);
            ArticleDO articleDO = null;

            if (cachedArticles.get(i) != null) {
                articleDO = (ArticleDO) cachedArticles.get(i); // 从缓存取
            } else {
                articleDO = missingArticlesMap.get(articleId); // 从 DB 查询结果里取
            }

            if (articleDO != null) {
                finalVOs.add(BeanUtil.copyProperties(articleDO, ArticleListVO.class));
            }
        }
        Long total = redisTemplate.opsForZSet().zCard(articleListKey);
        // 兜底处理：防止并发情况下瞬间失效
        if (total == null) total = 0L;
        return new PageResult<ArticleListVO>(total, finalVOs);
    }

    @Override
    @Transactional
    public ArticleLikeVO likeArticle(Long articleId, Integer status) {
        //先在TheadLocal中获取当前用户ID
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        //获取文章作者id
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL); // 或对应的枚举
        }

        Long authorId = article.getUserId();
        //根据文章id和用户id去查数据库，看有没有数据
        QueryWrapper<UserFootDO> wrapper = new QueryWrapper<UserFootDO>()
                .eq("article_id", articleId)
                .eq("user_id", currentUserId);
        UserFootDO userFootDO = userFootMapper.selectOne(wrapper);
        if(userFootDO == null){
            //第一次点赞必须是1
            if(status != 1){
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            //没有数据，表示第一次点赞，插入一条数据，状态为1，文章的点赞数加一
            userFootMapper.insert(UserFootDO
                            .builder()
                            .articleId(articleId)
                            .userId(currentUserId)
                            .articleUserId(authorId)
                            .likeStat(1)
                            .build()
                    );
            UpdateWrapper<ArticleDO> setLikeCountWrapper = new UpdateWrapper<ArticleDO>()
                    .setSql("like_count = like_count+1")
                    .eq("id", articleId);
            articleMapper.update(setLikeCountWrapper);
            return ArticleLikeVO.builder()
                    .likeCount(articleMapper.selectById(articleId).getLikeCount())
                    .likeStat(status)
                    .build();
        }
        //有数据，先看是点赞还是取消点赞
        //看数据库的原来点赞状态
        Integer likeStat = userFootDO.getLikeStat();
        if(status == 1){
            //1、点赞，先查数据库看状态是不是0，如果是就把状态改为1，把文章的点赞数加一，
            //   如果状态是1，就不做任何操作（防重发）
            if(likeStat == 0){
                UpdateWrapper<UserFootDO> setLikeStatWrapper = new UpdateWrapper<UserFootDO>()
                        .setSql("like_stat = 1")
                        .eq("article_id", articleId)
                        .eq("user_id", currentUserId);
                userFootMapper.update(setLikeStatWrapper);

                UpdateWrapper<ArticleDO> setLikeCountWrapper = new UpdateWrapper<ArticleDO>()
                        .setSql("like_count = like_count+1")
                        .eq("id", articleId);
                articleMapper.update(setLikeCountWrapper);
            }

        }
        else if(status == 0){
            //2、取消点赞，先查数据库看状态是不是1，如果是状态改为0，把文章的点赞数减一，
            //   如果状态是0，就不做任何操作（防重发）
            if(likeStat == 1){
                UpdateWrapper<UserFootDO> setLikeStatWrapper = new UpdateWrapper<UserFootDO>()
                        .setSql("like_stat = 0")
                        .eq("article_id", articleId)
                        .eq("user_id", currentUserId);
                userFootMapper.update(setLikeStatWrapper);

                UpdateWrapper<ArticleDO> setLikeCountWrapper = new UpdateWrapper<ArticleDO>()
                        .setSql("like_count = like_count-1")
                        .eq("id", articleId)
                        .gt("like_count", 0);
                articleMapper.update(setLikeCountWrapper);
            }
        }
        else{
            //3、其他情况，返回错误
             throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        return ArticleLikeVO
                .builder()
                .likeCount(articleMapper.selectById(articleId).getLikeCount())
                .likeStat(status)
                .build();
    }
}