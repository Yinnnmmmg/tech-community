package com.ying.tech.community.service.article.service.imlp;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.CursorPageResult;
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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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

        // 3. 维护文章时间轴索引 (ZSet)
        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        redisTemplate.opsForZSet().add(articleListKey, article.getId().toString(), System.currentTimeMillis());
        // 不设置 ZSet 过期时间，数据持久化
        // ZSet 瘦身，永远只保留最新的 5000 条数据，防止变成 Big Key
        redisTemplate.opsForZSet().removeRange(articleListKey, 0, -5001);
        log.info("文章 ID 已加入 Redis ZSet 时间轴排序列表");
        // 注意：具体的主表和详情表实体数据不再主动 set 进 Redis
        // 将装载具体内容缓存的任务，交给前端调用“查询文章详情”接口时去“懒加载”完成。

        return article.getId();
    }


    /**
     * 游标分页查询文章列表（基于 Redis ZSet 时间轴）
     *
     * <p>整体流程：
     * <ol>
     *   <li>从 Redis ZSet 中按发布时间降序取出本页的文章 ID 列表（游标控制翻页位置）</li>
     *   <li>用文章 ID 列表批量查询 Redis String 缓存，获取文章主表数据</li>
     *   <li>对缓存未命中的 ID 回表查 MySQL，并将查到的数据回写 Redis（Cache-Aside 模式）</li>
     *   <li>按 ZSet 返回的原始顺序重新组装 VO 列表，保证展示顺序与发布时间一致</li>
     * </ol>
     *
     * <p>游标设计说明：
     * <ul>
     *   <li>ZSet score = 文章发布时的毫秒时间戳，score 越大表示越新</li>
     *   <li>首次请求：cursor 传 null 或 0，maxScore 取当前时间，查最新的一页</li>
     *   <li>翻页请求：cursor 传上一页最后一条的 score，maxScore = cursor - 1，跳过已展示条目</li>
     *   <li>返回的 nextCursor 是本页最后一条（最旧一条）的 score，前端下次请求时原样传回</li>
     *   <li>nextCursor 为 null，表示已经没有更多数据，前端停止加载</li>
     * </ul>
     *
     * <p>缓存策略说明：
     * <ul>
     *   <li>ZSet 索引不设过期时间，作为持久化的时间轴排序结构</li>
     *   <li>文章主表缓存过期时间为 8 分钟 + [0, 60) 秒随机抖动，防止缓存雪崩</li>
     *   <li>回写缓存使用 multiSet 批量写入，减少 Redis 网络往返次数</li>
     * </ul>
     *
     * @param cursor   上一页最后一条记录的发布时间戳（毫秒），首次访问传 null 或 0
     * @param pageSize 每页条数
     * @return CursorPageResult，包含本页数据列表和下一页游标；nextCursor 为 null 表示已到末页
     */
    @Override
    public CursorPageResult<ArticleListVO> getArticleList(Long cursor, Integer pageSize) {
        log.info("游标查询文章列表，cursor: {}, pageSize: {}", cursor, pageSize);

        // 1. 从 ZSet 时间轴中取出本页文章 ID（按 score 降序，即发布时间从新到旧）
        // 首次请求 maxScore 取当前时间；翻页时取 cursor-1，跳过上一页最后一条，避免重复
        String articleListKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIST;
        long maxScore = (cursor == null || cursor <= 0) ? System.currentTimeMillis() : cursor-1;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                articleListKey,
                0,
                maxScore,
                0,
                pageSize);

        // 降级：ZSet只保存最近的5000篇文章id，如果为空有两种情况
        // 1、最近的文章已刷到底，或缓存丢失（生产环境可在此处补充 DB 游标查库兜底逻辑）
        if (typedTuples == null || typedTuples.isEmpty()) {
            ///TODO 最近的文章刷到底了，查数据库
            ///TODO 缓存丢失，用MQ发送消息，异步重建ZSet
            return new CursorPageResult<>(null, new ArrayList<>()); // nextCursor 为 null，告诉前端没数据了
        }

        // 遍历 ZSet 结果，按返回顺序收集文章 ID；
        // 遍历结束后 nextCursor 停在最后一条（score 最小，即最旧）的时间戳，作为下一页游标
        ArrayList<String> orderedIds = new ArrayList<>();
        Long nextCursor = null;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            orderedIds.add(typedTuple.getValue());
            nextCursor = typedTuple.getScore().longValue();
        }

        // 2. 批量查询 Redis String 缓存：将文章 ID 拼接成 Redis key，一次 multiGet 取回所有数据
        // multiGet 结果列表的大小和顺序与 keys 完全对应，方便后续按索引对齐
        List<String> keys = orderedIds.stream()
                .map(id -> RedisConstants.TECH_COMMUNITY_ARTICLE + id)
                .collect(Collectors.toList());
        List<Object> cachedArticles = redisTemplate.opsForValue().multiGet(keys);
        // multiGet 在 pipeline/cluster 场景下可能返回 null，兜底用等长的 null 列表替代，保证索引对齐
        if (cachedArticles == null) {
            cachedArticles = new ArrayList<>(Collections.nCopies(keys.size(), null));
        }

        // 3. 找出缓存未命中的文章 ID（缓存中对应位置为 null）
        List<String> missingIds = new ArrayList<>();
        // multiGet 的结果列表大小和顺序与 keys 完全一致
        for (int i = 0; i < cachedArticles.size(); i++) {
            if (cachedArticles.get(i) == null) {
                missingIds.add(orderedIds.get(i));
            }
        }

        // 4. 回表查库（Cache-Aside：缓存未命中时回源），并将结果批量回写 Redis
        Map<String, ArticleDO> missingArticlesMap = new HashMap<>();
        if (!missingIds.isEmpty()) {
            log.info("Redis 缓存缺失，批量回表查库，missingIds: {}", missingIds);
            List<ArticleDO> dbArticles = articleMapper.selectBatchIds(missingIds);

            // 同时构建：本地 Map（供第 5 步按 ID 快速查找）和 Redis 批量写入 Map
            Map<String, Object> redisBatchData = new HashMap<>();
            for (ArticleDO article : dbArticles) {
                String idStr = article.getId().toString();
                missingArticlesMap.put(idStr, article);
                redisBatchData.put(RedisConstants.TECH_COMMUNITY_ARTICLE + idStr, article);
            }

            // 性能优化：multiSet 一次性批量写入，减少网络往返；
            // 过期时间 8 分钟 + [0, 60) 秒随机抖动，防止大量 key 同时失效引发缓存雪崩
            if (!redisBatchData.isEmpty()) {
                redisTemplate.opsForValue().multiSet(redisBatchData);
                for (String key : redisBatchData.keySet()) {
                    redisTemplate.expire(key, 8*60 +
                            ThreadLocalRandom.current().nextLong(0,61), TimeUnit.SECONDS);
                }
            }
        }

        // 5. 按 ZSet 返回的原始顺序重新组装 VO 列表
        // 优先从缓存取，缓存缺失则从第 4 步查库结果中补充，保证最终列表顺序与发布时间一致
        List<ArticleListVO> finalVOs = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            String articleId = orderedIds.get(i);
            ArticleDO articleDO = null;

            if (cachedArticles.get(i) != null) {
                articleDO = (ArticleDO) cachedArticles.get(i); // 缓存命中
            } else {
                articleDO = missingArticlesMap.get(articleId); // 缓存未命中，从回表结果中取
            }

            if (articleDO != null) {
                finalVOs.add(BeanUtil.copyProperties(articleDO, ArticleListVO.class));
            }
        }
        // ZSet 返回条数不足 pageSize，说明已到末页，将 nextCursor 置 null 通知前端停止加载
        // 注意：必须以 ZSet 实际返回数（orderedIds.size()）为准，而非 finalVOs.size()。
        // 若以 finalVOs 判断，当 ZSet 里存在已被数据库删除的文章 ID 时，
        // finalVOs 会偏少，导致游标被错误置 null，后续数据永远加载不到。
        if (orderedIds.size() < pageSize) {
            nextCursor = null;
        }

        return new CursorPageResult<>(nextCursor, finalVOs);
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
        //TODO  存在并发竞态 Bug
        // 场景复现：同一用户同时发出两个点赞请求（如快速双击）：
        // 结果：用户点赞成功，但被并发请求意外取消。add 和 remove 这两步不是原子操作，必须用 Lua 脚本将 SISMEMBER + SADD/SREM 合并为一个原子操作。
        Long addResult = redisTemplate.opsForSet().add(likeKey, currentUserId);
        Long likeStat = addResult;
        if(Long.valueOf(0).equals(addResult)) {
            redisTemplate.opsForSet().remove(likeKey, currentUserId);
        }
        // 动态续期：每次点赞/取消点赞操作后刷新过期时间，长过期时间 30 天
        redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);
        //TODO 点赞关系数据先放在redis，后期改成mp实现异步单条落库
        return ArticleLikeVO.builder()
                .likeCount(redisTemplate.opsForSet().size(likeKey))//总条数
                .likeStat(likeStat)
                .build();
    }
}