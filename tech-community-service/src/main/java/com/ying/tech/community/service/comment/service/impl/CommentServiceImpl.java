package com.ying.tech.community.service.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.comment.entiry.CommentDO;
import com.ying.tech.community.service.comment.message.CommentPublishMessage;
import com.ying.tech.community.service.comment.repository.mapper.CommentMapper;
import com.ying.tech.community.service.comment.req.CommentPublishReq;
import com.ying.tech.community.service.comment.service.CommentCacheService;
import com.ying.tech.community.service.comment.service.CommentService;
import com.ying.tech.community.service.comment.vo.CommentArticlePageVO;
import com.ying.tech.community.service.comment.vo.CommentLikeVO;
import com.ying.tech.community.service.comment.vo.CommentListItemVO;
import com.ying.tech.community.service.comment.vo.CommentReplyPageVO;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.message.RedisLikeToDBMessage;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {
    private static final int DOCUMENT_TYPE_COMMENT = 2;
    private static final int DOCUMENT_TYPE_ARTICLE = 1;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserFootMapper userFootMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CommentCacheService commentCacheService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private Executor taskExecutor;

    /**
     * 发布评论
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long commentPublish(CommentPublishReq req) {
        //发布前的业务校验
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        //校验文章是否存在且允许评论
        validateArticle(req.getArticleId());
        //校验评论树结构是否合法
        validateCommentTree(req);

        //落库
        CommentDO commentDO = new CommentDO();
        commentDO.setArticleId(req.getArticleId());
        commentDO.setUserId(userId);
        commentDO.setParentCommentId(req.getParentCommentId());
        commentDO.setReplyToCommentId(req.getReplyToCommentId());
        commentDO.setReplyToUserId(req.getReplyToUserId());
        commentDO.setContent(req.getContent());
        commentDO.setStatus(PublishStatusConstants.PENDING);
        commentMapper.insert(commentDO);

        //异步发消息审核并完成副作用
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CommentPublishMessage message = new CommentPublishMessage();
                message.setCommentId(commentDO.getId());
                message.setAuthorId(userId);
                message.setPublishTime(System.currentTimeMillis());
                String commentId = UUID.randomUUID().toString();
                CorrelationData correlationData = new CorrelationData(commentId);
                taskExecutor.execute(() -> rabbitTemplate.convertAndSend(
                        "comment.publish.fanout", "", message, msg -> {
                            msg.getMessageProperties().setMessageId(commentId);
                            return msg;
                        }, correlationData));
            }
        });
        return commentDO.getId();
    }

    /**
     * 查询文章下的评论列表。
     */
    @Override
    public CommentArticlePageVO getArticleCommentList(Long articleId, Integer page, Integer size) {
        //先校验文章和分页参数
        validateArticle(articleId);
        validatePage(page, size);
        Long currentUserId = getCurrentUserId();
        Long articleAuthorId = articleMapper.selectById(articleId).getUserId();

        //公开分页走 Redis 分页缓存，mine 继续直接查数据库
        PageResult<CommentListItemVO> publicPage = commentCacheService.getArticlePublicPage(articleId, page, size,
                () -> queryArticlePublicPage(articleId, page, size));
        fillDynamicFields(publicPage.getRecords(), currentUserId, articleAuthorId);

        CommentArticlePageVO pageVO = new CommentArticlePageVO();
        pageVO.setPublicPage(publicPage);
        pageVO.setMine(currentUserId == null
                ? Collections.emptyList()
                : queryArticleMineComments(articleId, currentUserId, articleAuthorId));
        return pageVO;
    }

    /**
     * 查询顶层评论下的回复列表。
     */
    @Override
    public CommentReplyPageVO getCommentReplies(Long commentId, Integer page, Integer size) {
        //只允许按顶层评论查询回复，避免查询语义混乱
        validatePage(page, size);
        CommentDO topComment = commentMapper.selectById(commentId);
        if (topComment == null || topComment.getParentCommentId() != null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        Long currentUserId = getCurrentUserId();
        Long articleAuthorId = articleMapper.selectById(topComment.getArticleId()).getUserId();

        PageResult<CommentListItemVO> publicPage = commentCacheService.getReplyPublicPage(commentId, page, size,
                () -> queryReplyPublicPage(topComment.getArticleId(), commentId, page, size));
        fillDynamicFields(publicPage.getRecords(), currentUserId, articleAuthorId);

        CommentReplyPageVO pageVO = new CommentReplyPageVO();
        pageVO.setPublicPage(publicPage);
        pageVO.setMine(currentUserId == null
                ? Collections.emptyList()
                : queryReplyMineComments(topComment.getArticleId(), commentId, currentUserId, articleAuthorId));
        return pageVO;
    }

    /**
     * 切换评论点赞状态，并通过 MQ 异步落库。
     */
    @Override
    public CommentLikeVO likeComment(Long commentId) {
        CommentDO commentDO = requireApprovedComment(commentId);
        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        String likeKey = RedisConstants.TECH_COMMUNITY_COMMENT_LIKE + commentId;

        //首次命中时把数据库里的已点赞用户回填到 Redis Set
        if (Boolean.FALSE.equals(redisTemplate.hasKey(likeKey))) {
            List<UserFootDO> userFootDOList = userFootMapper.selectList(new QueryWrapper<UserFootDO>()
                    .eq("document_id", commentId)
                    .eq("document_type", DOCUMENT_TYPE_COMMENT)
                    .eq("like_stat", 1));
            List<Long> likedUserIds = userFootDOList.stream()
                    .map(UserFootDO::getUserId)
                    .toList();
            if (!likedUserIds.isEmpty()) {
                redisTemplate.opsForSet().add(likeKey, likedUserIds.toArray());
                redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);
            }
        }

        //Set add 返回 1 表示本次新增成功，否则说明已点过赞，走取消点赞
        Long addResult = redisTemplate.opsForSet().add(likeKey, currentUserId);
        Long likeStat = Long.valueOf(1L).equals(addResult) ? 1L : 0L;
        if (Long.valueOf(0L).equals(likeStat)) {
            redisTemplate.opsForSet().remove(likeKey, currentUserId);
        }
        redisTemplate.expire(likeKey, 30, TimeUnit.DAYS);

        //异步落库
        RedisLikeToDBMessage message = RedisLikeToDBMessage.builder()
                .userId(currentUserId)
                .documentId(commentId)
                .documentUserId(commentDO.getUserId())
                .readStat(0)
                .likeStat(likeStat.intValue())
                .build();
        taskExecutor.execute(() -> {
            try {
                String messageId = UUID.randomUUID().toString();
                CorrelationData correlationData = new CorrelationData(messageId);
                rabbitTemplate.convertAndSend("comment.publish.direct", "comment.like", message, msg -> {
                    msg.getMessageProperties().setCorrelationId(messageId);
                    msg.getMessageProperties().setMessageId(messageId);
                    return msg;
                }, correlationData);
            } catch (Exception e) {
                log.error("send comment like message failed, commentId={}, userId={}", commentId, currentUserId, e);
            }
        });

        return CommentLikeVO.builder()
                .likeCount(redisTemplate.opsForSet().size(likeKey))
                .likeStat(likeStat)
                .build();
    }

    /**
     * 删除评论。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteComment(Long commentId) {
        CommentDO commentDO = commentMapper.selectById(commentId);
        if (commentDO == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        ArticleDO articleDO = articleMapper.selectById(commentDO.getArticleId());
        if (articleDO == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }

        Long currentUserId = ReqInfoContext.getReqInfo().getUserId();
        //只有评论作者和文章作者可以删除
        if (!Objects.equals(currentUserId, commentDO.getUserId()) && !Objects.equals(currentUserId, articleDO.getUserId())) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //收集被删除评论 ID，用于事务提交后清理点赞缓存
        List<Long> deletedCommentIds = new ArrayList<>();
        deletedCommentIds.add(commentDO.getId());

        if (commentDO.getParentCommentId() == null) {
            deleteTopComment(commentDO, articleDO, deletedCommentIds);
        } else {
            deleteReplyComment(commentDO, articleDO, deletedCommentIds);
        }
    }

    /**
     * 校验文章是否存在且允许评论
     */
    private void validateArticle(Long articleId) {
        ArticleDO articleDO = articleMapper.selectById(articleId);
        if (articleDO == null) {
            throw new BusinessException(StatusEnum.ARTICLE_NOT_FOUND);
        }
        if (!Objects.equals(articleDO.getStatus(), PublishStatusConstants.APPROVED)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 校验评论树结构是否合法
     */
    private void validateCommentTree(CommentPublishReq req) {
        Long parentCommentId = req.getParentCommentId();
        Long replyToCommentId = req.getReplyToCommentId();
        Long replyToUserId = req.getReplyToUserId();

        //一级评论：不允许携带回复目标
        if (parentCommentId == null) {
            if (replyToCommentId != null || replyToUserId != null) {
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            return;
        }

        //二级回复：回复目标必须完整
        if (replyToCommentId == null || replyToUserId == null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        CommentDO parentComment = commentMapper.selectById(parentCommentId);
        if (!isSameArticleComment(parentComment, req.getArticleId())) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //二级回复的 parentCommentId 必须指向顶层评论
        if (parentComment.getParentCommentId() != null) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        CommentDO replyToComment = commentMapper.selectById(replyToCommentId);
        if (!isSameArticleComment(replyToComment, req.getArticleId())) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //replyToUserId 必须和被回复评论作者一致
        if (!Objects.equals(replyToComment.getUserId(), replyToUserId)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }

        //被回复评论必须属于当前顶层评论树，禁止构造三级评论
        if (!belongsToCurrentTopComment(replyToComment, parentCommentId)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 校验评论是否存在且属于当前文章
     */
    private boolean isSameArticleComment(CommentDO commentDO, Long articleId) {
        return commentDO != null && Objects.equals(commentDO.getArticleId(), articleId);
    }

    /**
     * 校验被回复评论是否属于当前顶层评论树
     */
    private boolean belongsToCurrentTopComment(CommentDO replyToComment, Long parentCommentId) {
        if (replyToComment.getParentCommentId() == null) {
            return Objects.equals(replyToComment.getId(), parentCommentId);
        }
        return Objects.equals(replyToComment.getParentCommentId(), parentCommentId);
    }

    /**
     * 校验分页参数是否合法。
     */
    private void validatePage(Integer page, Integer size) {
        if (page == null || size == null || page <= 0 || size <= 0) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 查询文章维度的公开顶层评论分页。
     */
    private PageResult<CommentListItemVO> queryArticlePublicPage(Long articleId, Integer page, Integer size) {
        QueryWrapper<CommentDO> countWrapper = new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .isNull("parent_comment_id")
                .eq("status", PublishStatusConstants.APPROVED)
                .orderByDesc("create_time");
        long total = commentMapper.selectCount(countWrapper);
        if (total <= 0) {
            return new PageResult<>(0L, Collections.emptyList());
        }

        Page<CommentDO> commentPage = commentMapper.selectPage(new Page<>(page, size), new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .isNull("parent_comment_id")
                .eq("status", PublishStatusConstants.APPROVED)
                .orderByDesc("create_time"));
        return new PageResult<>(commentPage.getTotal(), buildCommentVOList(commentPage.getRecords()));
    }

    /**
     * 查询顶层评论下的公开回复分页。
     */
    private PageResult<CommentListItemVO> queryReplyPublicPage(Long articleId, Long parentCommentId, Integer page, Integer size) {
        QueryWrapper<CommentDO> countWrapper = new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .eq("parent_comment_id", parentCommentId)
                .eq("status", PublishStatusConstants.APPROVED)
                .orderByAsc("create_time");
        long total = commentMapper.selectCount(countWrapper);
        if (total <= 0) {
            return new PageResult<>(0L, Collections.emptyList());
        }

        Page<CommentDO> commentPage = commentMapper.selectPage(new Page<>(page, size), new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .eq("parent_comment_id", parentCommentId)
                .eq("status", PublishStatusConstants.APPROVED)
                .orderByAsc("create_time"));
        return new PageResult<>(commentPage.getTotal(), buildCommentVOList(commentPage.getRecords()));
    }

    /**
     * 查询当前用户在文章下自己的待审核/驳回顶层评论。
     */
    private List<CommentListItemVO> queryArticleMineComments(Long articleId, Long currentUserId, Long articleAuthorId) {
        List<CommentDO> mineComments = commentMapper.selectList(new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .eq("user_id", currentUserId)
                .isNull("parent_comment_id")
                .in("status", PublishStatusConstants.PENDING, PublishStatusConstants.REJECTED)
                .orderByDesc("create_time"));
        List<CommentListItemVO> records = buildCommentVOList(mineComments);
        fillDynamicFields(records, currentUserId, articleAuthorId);
        return records;
    }

    /**
     * 查询当前用户在顶层评论下自己的待审核/驳回复。
     */
    private List<CommentListItemVO> queryReplyMineComments(Long articleId, Long parentCommentId,
                                                           Long currentUserId, Long articleAuthorId) {
        List<CommentDO> mineReplies = commentMapper.selectList(new QueryWrapper<CommentDO>()
                .eq("article_id", articleId)
                .eq("parent_comment_id", parentCommentId)
                .eq("user_id", currentUserId)
                .in("status", PublishStatusConstants.PENDING, PublishStatusConstants.REJECTED)
                .orderByDesc("create_time"));
        List<CommentListItemVO> records = buildCommentVOList(mineReplies);
        fillDynamicFields(records, currentUserId, articleAuthorId);
        return records;
    }

    /**
     * 组装评论基础 VO。
     *
     * <p>这里只填充与当前用户无关的公共字段，likeStat 和 canDelete 在返回前再动态回填。
     */
    private List<CommentListItemVO> buildCommentVOList(List<CommentDO> commentDOList) {
        if (commentDOList == null || commentDOList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = new HashSet<>();
        for (CommentDO commentDO : commentDOList) {
            userIds.add(commentDO.getUserId());
            if (commentDO.getReplyToUserId() != null) {
                userIds.add(commentDO.getReplyToUserId());
            }
        }
        Map<Long, String> usernameMap = loadUsernameMap(userIds);

        List<CommentListItemVO> records = new ArrayList<>(commentDOList.size());
        for (CommentDO commentDO : commentDOList) {
            CommentListItemVO itemVO = new CommentListItemVO();
            itemVO.setCommentId(commentDO.getId());
            itemVO.setArticleId(commentDO.getArticleId());
            itemVO.setUserId(commentDO.getUserId());
            itemVO.setUsername(usernameMap.get(commentDO.getUserId()));
            itemVO.setContent(commentDO.getContent());
            itemVO.setStatus(commentDO.getStatus());
            itemVO.setRejectReason(commentDO.getRejectReason());
            itemVO.setLikeCount(commentDO.getLikeCount() == null ? 0L : commentDO.getLikeCount().longValue());
            itemVO.setLikeStat(0L);
            itemVO.setReplyCount(commentDO.getReplyCount() == null ? 0L : commentDO.getReplyCount().longValue());
            itemVO.setReplyToUserId(commentDO.getReplyToUserId());
            itemVO.setReplyToUsername(commentDO.getReplyToUserId() == null ? null : usernameMap.get(commentDO.getReplyToUserId()));
            itemVO.setCreateTime(formatTime(commentDO.getCreateTime()));
            itemVO.setCanDelete(Boolean.FALSE);
            records.add(itemVO);
        }
        return records;
    }

    /**
     * 回填与当前用户相关的动态字段。
     */
    private void fillDynamicFields(List<CommentListItemVO> records, Long currentUserId, Long articleAuthorId) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<Long> commentIds = records.stream()
                .map(CommentListItemVO::getCommentId)
                .toList();
        Set<Long> likedCommentIds = queryLikedCommentIds(commentIds, currentUserId);
        for (CommentListItemVO record : records) {
            String likeKey = RedisConstants.TECH_COMMUNITY_COMMENT_LIKE + record.getCommentId();
            Long likeStat = currentUserId == null
                    ? 0L
                    : (Boolean.TRUE.equals(redisTemplate.hasKey(likeKey))
                    ? (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(likeKey, currentUserId)) ? 1L : 0L)
                    : (likedCommentIds.contains(record.getCommentId()) ? 1L : 0L));
            record.setLikeStat(likeStat);
            record.setLikeCount(resolveLikeCount(record.getCommentId(), record.getLikeCount()));
            boolean canDelete = Objects.equals(currentUserId, record.getUserId())
                    || Objects.equals(currentUserId, articleAuthorId);
            record.setCanDelete(canDelete);
        }
    }

    /**
     * 查询当前用户在指定评论集合上的点赞状态。
     */
    private Long getCurrentUserId() {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        return reqInfo == null ? null : reqInfo.getUserId();
    }

    private Set<Long> queryLikedCommentIds(List<Long> commentIds, Long currentUserId) {
        if (commentIds == null || commentIds.isEmpty() || currentUserId == null) {
            return Collections.emptySet();
        }

        List<UserFootDO> likedFootList = userFootMapper.selectList(new QueryWrapper<UserFootDO>()
                .eq("user_id", currentUserId)
                .eq("document_type", DOCUMENT_TYPE_COMMENT)
                .eq("like_stat", 1)
                .in("document_id", commentIds));
        if (likedFootList == null || likedFootList.isEmpty()) {
            return Collections.emptySet();
        }
        return likedFootList.stream()
                .map(UserFootDO::getDocumentId)
                .collect(Collectors.toSet());
    }

    /**
     * 点赞数优先读 Redis Set，未命中时回退数据库值。
     */
    private Long resolveLikeCount(Long commentId, Long dbLikeCount) {
        String likeKey = RedisConstants.TECH_COMMUNITY_COMMENT_LIKE + commentId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(likeKey))) {
            Long likeCount = redisTemplate.opsForSet().size(likeKey);
            return likeCount == null ? 0L : likeCount;
        }
        return dbLikeCount == null ? 0L : dbLikeCount;
    }

    /**
     * 批量加载用户名映射。
     */
    private Map<Long, String> loadUsernameMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserDO> userDOList = userMapper.selectBatchIds(userIds);
        if (userDOList == null || userDOList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> usernameMap = new HashMap<>();
        for (UserDO userDO : userDOList) {
            usernameMap.put(userDO.getId(), userDO.getUsername());
        }
        return usernameMap;
    }

    /**
     * 校验评论存在且已审核通过。
     */
    private CommentDO requireApprovedComment(Long commentId) {
        CommentDO commentDO = commentMapper.selectById(commentId);
        if (commentDO == null || !Objects.equals(commentDO.getStatus(), PublishStatusConstants.APPROVED)) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        return commentDO;
    }

    /**
     * 删除顶层评论及其所有二级回复。
     */
    private void deleteTopComment(CommentDO commentDO, ArticleDO articleDO, List<Long> deletedCommentIds) {
        List<CommentDO> replyList = commentMapper.selectList(new QueryWrapper<CommentDO>()
                .eq("parent_comment_id", commentDO.getId()));
        for (CommentDO reply : replyList) {
            deletedCommentIds.add(reply.getId());
        }

        long approvedReplyCount = replyList.stream()
                .filter(reply -> Objects.equals(reply.getStatus(), PublishStatusConstants.APPROVED))
                .count();
        long approvedDeletedCount = approvedReplyCount
                + (Objects.equals(commentDO.getStatus(), PublishStatusConstants.APPROVED) ? 1 : 0);

        if (!replyList.isEmpty()) {
            commentMapper.delete(new QueryWrapper<CommentDO>().eq("parent_comment_id", commentDO.getId()));
        }
        commentMapper.deleteById(commentDO.getId());

        //只有公开评论才回滚聚合计数和公开分页缓存
        if (approvedDeletedCount > 0) {
            articleMapper.update(null, new UpdateWrapper<ArticleDO>()
                    .eq("id", articleDO.getId())
                    .setSql("comment_count = CASE WHEN COALESCE(comment_count, 0) < " + approvedDeletedCount
                            + " THEN 0 ELSE COALESCE(comment_count, 0) - " + approvedDeletedCount + " END"));
            registerAfterCommit(() -> {
                commentCacheService.bumpArticleListVersion(articleDO.getId());
                commentCacheService.clearArticleSummaryCache(articleDO.getId());
                commentCacheService.clearCommentLikeCache(deletedCommentIds);
            });
            return;
        }

        registerAfterCommit(() -> commentCacheService.clearCommentLikeCache(deletedCommentIds));
    }

    /**
     * 删除二级回复。
     */
    private void deleteReplyComment(CommentDO commentDO, ArticleDO articleDO, List<Long> deletedCommentIds) {
        commentMapper.deleteById(commentDO.getId());

        if (Objects.equals(commentDO.getStatus(), PublishStatusConstants.APPROVED)) {
            articleMapper.update(null, new UpdateWrapper<ArticleDO>()
                    .eq("id", articleDO.getId())
                    .setSql("comment_count = CASE WHEN COALESCE(comment_count, 0) <= 0 THEN 0 ELSE COALESCE(comment_count, 0) - 1 END"));
            commentMapper.update(null, new UpdateWrapper<CommentDO>()
                    .eq("id", commentDO.getParentCommentId())
                    .setSql("reply_count = CASE WHEN COALESCE(reply_count, 0) <= 0 THEN 0 ELSE COALESCE(reply_count, 0) - 1 END"));
            registerAfterCommit(() -> {
                commentCacheService.bumpArticleListVersion(articleDO.getId());
                commentCacheService.bumpReplyListVersion(commentDO.getParentCommentId());
                commentCacheService.clearArticleSummaryCache(articleDO.getId());
                commentCacheService.clearCommentLikeCache(deletedCommentIds);
            });
            return;
        }

        registerAfterCommit(() -> commentCacheService.clearCommentLikeCache(deletedCommentIds));
    }

    /**
     * 格式化时间字段。
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    /**
     * 注册事务提交后的回调，避免数据库回滚时误删缓存。
     */
    private void registerAfterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
