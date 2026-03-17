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
import java.util.concurrent.TimeUnit;
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

        // 1. 插入文章主表
        ArticleDO article = new ArticleDO();
        BeanUtil.copyProperties(articlePostReq, article);
        article.setUserId(userId);
        articleMapper.insert(article);
        log.info("文章表插入数据库成功，文章 ID: {}", article.getId());

        // 2. 插入文章细节表
        ArticleDetailDO articleDetail = new ArticleDetailDO();
        BeanUtil.copyProperties(articlePostReq, articleDetail);
        articleDetail.setArticleId(article.getId());
        articleDetailMapper.insert(articleDetail);
        log.info("文章详情表插入成功，详情 ID: {}", articleDetail.getId());

        // 3. 维护文章时间轴索引 (ZSet) —— 极佳的设计！保留！
        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        redisTemplate.opsForZSet().add(articleListKey, article.getId().toString(), System.currentTimeMillis());
        // 设置 ZSet 过期时间：文章列表追求高时效性，短过期时间 8 分钟
        redisTemplate.expire(articleListKey, 8, TimeUnit.MINUTES);
        log.info("文章 ID 已加入 Redis ZSet 时间轴排序列表");

        // 注意：具体的主表和详情表实体数据不再主动 set 进 Redis
        // 将装载具体内容缓存的任务，交给前端调用“查询文章详情”接口时去“懒加载”完成。

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
                // 为每个文章缓存设置过期时间：文章列表追求高时效性，短过期时间 8 分钟
                for (String key : redisBatchData.keySet()) {
                    redisTemplate.expire(key, 8, TimeUnit.MINUTES);
                }
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

    /**
     * 用redis 实现点赞功能
     * */
    @Override
    public ArticleLikeVO likeArticle(Long articleId) {
        //先在TheadLocal中获取当前用户ID
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        //拼接 key
        String likeKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIKE + articleId;
        // 【核心兜底逻辑】：检查 Redis 中是否存在该 Key,预防redis宕机导致redis数据丢失，添加完这条点赞后点赞数变成了1
        if (Boolean.FALSE.equals(redisTemplate.hasKey(likeKey))) {
            // 如果 Redis 里没有，去 MySQL 的 user_foot 表查出这篇文章所有点过赞的 userId
            // 通过文章id查出点过赞的 userId,,注意like_stat为0的不要算进去了
            List<UserFootDO> userFootDOList = userFootMapper.selectList
                                             (new QueryWrapper<UserFootDO>()
                                                     .eq("article_id", articleId)
                                                     .eq("like_stat", 1));
            List<Long> likedUserIds = userFootDOList
                                        .stream()
                                        .map(userFootDO -> userFootDO.getUserId())
                                        .collect(Collectors.toList());
            if (likedUserIds != null && !likedUserIds.isEmpty()) {
                // 把查出来的历史点赞用户，一次性塞进 Redis 里恢复现场
                redisTemplate.opsForSet().add(likeKey, likedUserIds.toArray());
                // 设置点赞 key 的过期时间：长过期时间 30 天
                redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);
            }
        }
        //添加到set中，返回1表示添加成功（点赞成功），
        //返回0表示添加失败（已经点过赞了），所以是取消点赞，删除
        Long addResult = redisTemplate.opsForSet().add(likeKey, currentUserId);
        if(addResult == 0) {
            redisTemplate.opsForSet().remove(likeKey, currentUserId);
        }
        // 动态续期：每次点赞/取消点赞操作后刷新过期时间，长过期时间 30 天
        redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);
        //TODO 点赞关系数据先放在redis，后期改成mp实现异步单条落库
        return ArticleLikeVO.builder()
                .likeCount(redisTemplate.opsForSet().size(likeKey))//总条数
                .likeStat(addResult)
                .build();
    }
}