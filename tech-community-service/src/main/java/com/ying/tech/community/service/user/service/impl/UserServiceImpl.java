package com.ying.tech.community.service.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.core.constants.PublishStatusConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleService;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.notifyMsg.message.UserFollowNotifyMessage;
import com.ying.tech.community.service.sms.service.SmsService;
import com.ying.tech.community.service.sms.service.SmsVerificationService;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.entity.UserInfoDO;
import com.ying.tech.community.service.user.entity.UserFootDO;
import com.ying.tech.community.service.user.entity.UserRelationDO;
import com.ying.tech.community.service.user.repository.mapper.UserFootMapper;
import com.ying.tech.community.service.user.repository.mapper.UserInfoMapper;
import com.ying.tech.community.service.user.repository.mapper.UserMapper;
import com.ying.tech.community.service.user.repository.mapper.UserRelationMapper;
import com.ying.tech.community.service.user.req.ChangePasswordReq;
import com.ying.tech.community.service.user.req.UserProfileUpdateReq;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.service.UserService;
import com.ying.tech.community.service.user.vo.FollowActionVO;
import com.ying.tech.community.service.user.vo.FollowStatsVO;
import com.ying.tech.community.service.user.vo.UserCurrentVO;
import com.ying.tech.community.service.user.vo.UserFollowListItemVO;
import com.ying.tech.community.service.user.vo.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 *
 * <p>负责注册登录、用户信息查询以及关注关系的增删查和消息通知。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DOCUMENT_TYPE_ARTICLE = 1;

    /** 已关注状态。 */
    private static final int FOLLOW_STATE_FOLLOWED = 1;
    /** 已取消关注状态。 */
    private static final int FOLLOW_STATE_UNFOLLOWED = 2;
    /** 通知交换机。 */
    private static final String NOTIFY_EXCHANGE = "notify.direct";
    /** 关注通知路由键。 */
    private static final String FOLLOW_NOTIFY_ROUTING_KEY = "user.follow.notify";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    private UserRelationMapper userRelationMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private UserFootMapper userFootMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private SmsService smsService;
    @Autowired
    private SmsVerificationService smsVerificationService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 根据用户名查询用户。
     */
    @Override
    public UserDO getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
    }

    /**
     * 根据手机号查询用户。
     */
    @Override
    public UserDO getByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getPhone, phone));
    }

    /**
     * 用户注册。
     *
     * <p>支持手机号注册（phone + smsCode）和用户名注册。密码使用 BCrypt 加密。
     */
    @Override
    public Long register(UserSaveReq req) {
        boolean isPhoneReg = StringUtils.hasText(req.getPhone());

        if (isPhoneReg) {
            if (!req.getPhone().matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(StatusEnum.PHONE_FORMAT_ERROR);
            }
            if (!StringUtils.hasText(req.getSmsCode())) {
                throw new RuntimeException("验证码不能为空");
            }
            smsVerificationService.verifyCode(req.getPhone(), req.getSmsCode());

            UserDO existPhoneUser = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getPhone, req.getPhone()));
            if (existPhoneUser != null) {
                throw new BusinessException(StatusEnum.PHONE_ALREADY_REGISTERED);
            }
        } else {
            if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
                throw new RuntimeException("用户名或密码不能为空");
            }
            UserDO existUser = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                    .eq(UserDO::getUsername, req.getUsername()));
            if (existUser != null) {
                throw new BusinessException(StatusEnum.USER_EXISTS);
            }
        }

        if (!StringUtils.hasText(req.getPassword())) {
            throw new RuntimeException("密码不能为空");
        }

        String encodedPwd = passwordEncoder.encode(req.getPassword());

        String username = isPhoneReg ? req.getPhone() : req.getUsername();

        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(encodedPwd);
        if (isPhoneReg) {
            user.setPhone(req.getPhone());
        }
        user.setThirdAccountId(null);
        user.setLoginType(0);
        user.setUserRole(0);
        user.setDeleted(0);
        userMapper.insert(user);

        UserInfoDO userInfo = new UserInfoDO();
        userInfo.setUserId(user.getId());
        userInfo.setUsername(username);
        userInfo.setIp("{}");
        userInfo.setDeleted(0);
        userInfoMapper.insert(userInfo);

        return user.getId();
    }

    /**
     * 用户登录并返回 Token（用户名 + 密码）。
     * <p>先尝试 BCrypt 校验，失败时回退 MD5 兼容老用户。
     */
    @Override
    public String login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("用户名或密码不能为空");
        }

        UserDO user = getByUsername(username);
        if (user == null) {
            throw new BusinessException(StatusEnum.USER_NOT_FOUND);
        }

        if (!verifyPassword(password, user.getPassword())) {
            throw new BusinessException(StatusEnum.USER_PWD_ERROR);
        }

        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    /**
     * 手机号登录（双模式：密码或短信验证码）。
     */
    @Override
    public String loginByPhone(String phone, String password, String smsCode) {
        if (!StringUtils.hasText(phone)) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(StatusEnum.PHONE_FORMAT_ERROR);
        }

        UserDO user = getByPhone(phone);
        if (user == null) {
            throw new BusinessException(StatusEnum.USER_NOT_FOUND);
        }

        if (StringUtils.hasText(smsCode)) {
            smsVerificationService.verifyCode(phone, smsCode);
        } else if (StringUtils.hasText(password)) {
            if (!verifyPassword(password, user.getPassword())) {
                throw new BusinessException(StatusEnum.USER_PWD_ERROR);
            }
        } else {
            throw new RuntimeException("密码或验证码不能为空");
        }

        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }

    /**
     * 发送短信验证码。
     */
    @Override
    public void sendSmsCode(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new RuntimeException("手机号不能为空");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(StatusEnum.PHONE_FORMAT_ERROR);
        }
        smsService.sendCode(phone);
    }

    /**
     * 验证密码，先尝试 BCrypt，再尝试 MD5（兼容老用户）。
     */
    private boolean verifyPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        }
        // MD5 fallback for legacy users
        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        return md5.equals(encodedPassword);
    }

    /**
     * 获取用户信息。
     */
    @Override
    public UserDO getUserInfo(Long userId) {
        return requireUser(userId);
    }

    @Override
    public UserCurrentVO getCurrentUser(Long userId) {
        UserDO user = requireUser(userId);
        UserInfoDO userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfoDO>()
                .eq(UserInfoDO::getUserId, userId)
                .last("limit 1"));

        UserCurrentVO currentUser = new UserCurrentVO();
        currentUser.setId(user.getId());
        currentUser.setUsername(resolveDisplayName(userId, user, userInfo));
        currentUser.setUserRole(normalizeRole(user.getUserRole()));
        if (userInfo != null) {
            currentUser.setPhoto(userInfo.getPhoto());
            currentUser.setPosition(userInfo.getPosition());
            currentUser.setCompany(userInfo.getCompany());
            currentUser.setProfile(userInfo.getProfile());
        }
        return currentUser;
    }

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        UserDO user = requireUser(userId);
        UserInfoDO userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfoDO>()
                .eq(UserInfoDO::getUserId, userId)
                .last("limit 1"));

        Long currentUserId = getCurrentUserId();
        UserProfileVO profile = new UserProfileVO();
        profile.setUserId(userId);
        profile.setUsername(resolveDisplayName(userId, user, userInfo));
        profile.setUserRole(normalizeRole(user.getUserRole()));
        profile.setPhoto(userInfo == null ? null : userInfo.getPhoto());
        profile.setPosition(userInfo == null ? null : userInfo.getPosition());
        profile.setCompany(userInfo == null ? null : userInfo.getCompany());
        profile.setProfile(userInfo == null ? null : userInfo.getProfile());
        profile.setArticleCount(countApprovedArticles(userId));
        profile.setFollowCount(countActiveFollows(userId));
        profile.setFanCount(countActiveFans(userId));
        profile.setCollectionCount(countUserFootStats(userId, UserFootDO::getCollectionStat));
        profile.setLikeCount(countUserFootStats(userId, UserFootDO::getLikeStat));
        profile.setSelf(Objects.equals(currentUserId, userId));
        profile.setFollowed(!Objects.equals(currentUserId, userId) && isFollowing(currentUserId, userId));
        profile.setCreateTime(formatTime(user.getCreateTime()));
        return profile;
    }

    @Override
    public void updateCurrentUserProfile(Long userId, UserProfileUpdateReq req) {
        UserDO user = requireUser(userId);
        UserInfoDO userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfoDO>()
                .eq(UserInfoDO::getUserId, userId)
                .last("limit 1"));
        if (userInfo == null) {
            userInfo = new UserInfoDO();
            userInfo.setUserId(userId);
            userInfo.setIp("{}");
        }

        userInfo.setUsername(normalizeText(req.getUsername()));
        userInfo.setPhoto(normalizeText(req.getPhoto()));
        userInfo.setPosition(normalizeText(req.getPosition()));
        userInfo.setCompany(normalizeText(req.getCompany()));
        userInfo.setProfile(normalizeText(req.getProfile()));

        if (!StringUtils.hasText(userInfo.getUsername())) {
            userInfo.setUsername(user.getUsername());
        }

        if (userInfo.getId() == null) {
            userInfoMapper.insert(userInfo);
            return;
        }
        userInfoMapper.updateById(userInfo);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordReq req) {
        if (!StringUtils.hasText(req.getOldPassword()) || !StringUtils.hasText(req.getNewPassword())) {
            throw new RuntimeException("密码不能为空");
        }
        UserDO user = requireUser(userId);
        if (!verifyPassword(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(StatusEnum.USER_PWD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public PageResult<ArticleListVO> getUserArticlePage(Long userId, Integer page, Integer size) {
        validatePage(page, size);
        requireUser(userId);
        return articleService.getApprovedArticlesByUser(userId, page, size);
    }

    @Override
    public PageResult<ArticleListVO> getUserCollectionArticlePage(Long userId, Integer page, Integer size) {
        return getSelfFootArticlePage(userId, page, size, true);
    }

    @Override
    public PageResult<ArticleListVO> getUserLikeArticlePage(Long userId, Integer page, Integer size) {
        return getSelfFootArticlePage(userId, page, size, false);
    }

    /**
     * 关注目标用户。
     *
     * <p>若关系不存在则插入，若已存在但处于取消状态则恢复，并在成功关注后投递通知。
     */
    @Override
    public FollowActionVO followUser(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        validateTargetUser(targetUserId, currentUserId, true);

        UserRelationDO relation = findRelation(targetUserId, currentUserId);
        boolean notifyNeeded = false;
        if (relation == null) {
            UserRelationDO newRelation = UserRelationDO.builder()
                    .userId(targetUserId)
                    .followUserId(currentUserId)
                    .followState(FOLLOW_STATE_FOLLOWED)
                    .build();
            try {
                userRelationMapper.insert(newRelation);
                notifyNeeded = true;
            } catch (DuplicateKeyException duplicateKeyException) {
                relation = findRelation(targetUserId, currentUserId);
                if (relation == null) {
                    throw duplicateKeyException;
                }
                if (!Objects.equals(relation.getFollowState(), FOLLOW_STATE_FOLLOWED)) {
                    relation.setFollowState(FOLLOW_STATE_FOLLOWED);
                    userRelationMapper.updateById(relation);
                    notifyNeeded = true;
                }
            }
        } else if (!Objects.equals(relation.getFollowState(), FOLLOW_STATE_FOLLOWED)) {
            relation.setFollowState(FOLLOW_STATE_FOLLOWED);
            userRelationMapper.updateById(relation);
            notifyNeeded = true;
        }

        if (notifyNeeded) {
            sendFollowNotify(currentUserId, targetUserId);
        }

        return FollowActionVO.builder()
                .targetUserId(targetUserId)
                .followed(Boolean.TRUE)
                .build();
    }

    /**
     * 取消关注目标用户。
     */
    @Override
    public FollowActionVO unfollowUser(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        validateTargetUser(targetUserId, currentUserId, true);

        UserRelationDO relation = findRelation(targetUserId, currentUserId);
        if (relation != null && !Objects.equals(relation.getFollowState(), FOLLOW_STATE_UNFOLLOWED)) {
            relation.setFollowState(FOLLOW_STATE_UNFOLLOWED);
            userRelationMapper.updateById(relation);
        }

        return FollowActionVO.builder()
                .targetUserId(targetUserId)
                .followed(Boolean.FALSE)
                .build();
    }

    /**
     * 查询当前用户对目标用户的关注状态。
     */
    @Override
    public FollowActionVO getFollowStatus(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        validateTargetUser(targetUserId, currentUserId, false);

        boolean followed = !Objects.equals(currentUserId, targetUserId)
                && isFollowing(currentUserId, targetUserId);
        return FollowActionVO.builder()
                .targetUserId(targetUserId)
                .followed(followed)
                .build();
    }

    /**
     * 查询指定用户的关注和粉丝统计。
     */
    @Override
    public FollowStatsVO getFollowStats(Long userId) {
        validateUserId(userId);
        requireUser(userId);

        Long currentUserId = getCurrentUserId();
        return FollowStatsVO.builder()
                .followCount(countActiveFollows(userId))
                .fanCount(countActiveFans(userId))
                .followed(!Objects.equals(currentUserId, userId) && isFollowing(currentUserId, userId))
                .build();
    }

    /**
     * 分页查询指定用户的关注列表。
     */
    @Override
    public PageResult<UserFollowListItemVO> getFollowList(Long userId, Integer page, Integer size) {
        validatePage(page, size);
        requireUser(userId);

        Page<UserRelationDO> relationPage = userRelationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserRelationDO>()
                        .eq(UserRelationDO::getFollowUserId, userId)
                        .eq(UserRelationDO::getFollowState, FOLLOW_STATE_FOLLOWED)
                        .orderByDesc(UserRelationDO::getUpdateTime)
                        .orderByDesc(UserRelationDO::getId));

        return buildUserRelationPage(relationPage, true);
    }

    /**
     * 分页查询指定用户的粉丝列表。
     */
    @Override
    public PageResult<UserFollowListItemVO> getFanList(Long userId, Integer page, Integer size) {
        validatePage(page, size);
        requireUser(userId);

        Page<UserRelationDO> relationPage = userRelationMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserRelationDO>()
                        .eq(UserRelationDO::getUserId, userId)
                        .eq(UserRelationDO::getFollowState, FOLLOW_STATE_FOLLOWED)
                        .orderByDesc(UserRelationDO::getUpdateTime)
                        .orderByDesc(UserRelationDO::getId));

        return buildUserRelationPage(relationPage, false);
    }

    /**
     * 将关注关系分页结果转换为前端展示对象。
     */
    private PageResult<UserFollowListItemVO> buildUserRelationPage(Page<UserRelationDO> relationPage, boolean followList) {
        List<UserRelationDO> relations = relationPage.getRecords();
        if (relations == null || relations.isEmpty()) {
            return new PageResult<>(relationPage.getTotal(), Collections.emptyList());
        }

        List<Long> itemUserIds = relations.stream()
                .map(relation -> followList ? relation.getUserId() : relation.getFollowUserId())
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserDO> userMap = loadUserMap(itemUserIds);
        Map<Long, UserInfoDO> userInfoMap = loadUserInfoMap(itemUserIds);
        Set<Long> followedUserIds = loadFollowedUserIds(getCurrentUserId(), itemUserIds);

        List<UserFollowListItemVO> records = relations.stream()
                .map(relation -> followList ? relation.getUserId() : relation.getFollowUserId())
                .map(itemUserId -> buildFollowListItem(itemUserId, userMap, userInfoMap, followedUserIds))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageResult<>(relationPage.getTotal(), records);
    }

    /**
     * 构建单个关注列表项。
     */
    private UserFollowListItemVO buildFollowListItem(Long itemUserId,
                                                     Map<Long, UserDO> userMap,
                                                     Map<Long, UserInfoDO> userInfoMap,
                                                     Set<Long> followedUserIds) {
        UserDO user = userMap.get(itemUserId);
        UserInfoDO userInfo = userInfoMap.get(itemUserId);
        if (user == null && userInfo == null) {
            return null;
        }

        return UserFollowListItemVO.builder()
                .userId(itemUserId)
                .username(resolveDisplayName(itemUserId, user, userInfo))
                .photo(userInfo == null ? null : userInfo.getPhoto())
                .position(userInfo == null ? null : userInfo.getPosition())
                .company(userInfo == null ? null : userInfo.getCompany())
                .profile(userInfo == null ? null : userInfo.getProfile())
                .followed(followedUserIds.contains(itemUserId))
                .build();
    }

    /**
     * 批量加载用户基础信息。
     */
    private Map<Long, UserDO> loadUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量加载用户扩展信息。
     */
    private Map<Long, UserInfoDO> loadUserInfoMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoMapper.selectList(new LambdaQueryWrapper<UserInfoDO>()
                        .in(UserInfoDO::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(UserInfoDO::getUserId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量查询当前用户已关注的候选用户集合。
     */
    private Set<Long> loadFollowedUserIds(Long currentUserId, Collection<Long> candidateUserIds) {
        if (currentUserId == null || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        return userRelationMapper.selectList(new LambdaQueryWrapper<UserRelationDO>()
                        .eq(UserRelationDO::getFollowUserId, currentUserId)
                        .eq(UserRelationDO::getFollowState, FOLLOW_STATE_FOLLOWED)
                        .in(UserRelationDO::getUserId, candidateUserIds))
                .stream()
                .map(UserRelationDO::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * 统计用户主动关注人数。
     */
    private long countActiveFollows(Long followerUserId) {
        Long count = userRelationMapper.selectCount(new LambdaQueryWrapper<UserRelationDO>()
                .eq(UserRelationDO::getFollowUserId, followerUserId)
                .eq(UserRelationDO::getFollowState, FOLLOW_STATE_FOLLOWED));
        return count == null ? 0L : count;
    }

    /**
     * 统计用户粉丝人数。
     */
    private long countActiveFans(Long targetUserId) {
        Long count = userRelationMapper.selectCount(new LambdaQueryWrapper<UserRelationDO>()
                .eq(UserRelationDO::getUserId, targetUserId)
                .eq(UserRelationDO::getFollowState, FOLLOW_STATE_FOLLOWED));
        return count == null ? 0L : count;
    }

    private long countApprovedArticles(Long userId) {
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<ArticleDO>()
                .eq(ArticleDO::getUserId, userId)
                .eq(ArticleDO::getStatus, PublishStatusConstants.APPROVED));
        return count == null ? 0L : count;
    }

    private long countUserFootStats(Long userId, SFunction<UserFootDO, Integer> fieldGetter) {
        Long count = userFootMapper.selectCount(new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getDocumentType, DOCUMENT_TYPE_ARTICLE)
                .eq(fieldGetter, 1));
        return count == null ? 0L : count;
    }

    private PageResult<ArticleListVO> getSelfFootArticlePage(Long userId, Integer page, Integer size, boolean collection) {
        validatePage(page, size);
        requireUser(userId);
        ensureSelfAccess(userId);

        LambdaQueryWrapper<UserFootDO> wrapper = new LambdaQueryWrapper<UserFootDO>()
                .eq(UserFootDO::getUserId, userId)
                .eq(UserFootDO::getDocumentType, DOCUMENT_TYPE_ARTICLE)
                .orderByDesc(UserFootDO::getUpdateTime)
                .orderByDesc(UserFootDO::getId);

        if (collection) {
            wrapper.eq(UserFootDO::getCollectionStat, 1);
        } else {
            wrapper.eq(UserFootDO::getLikeStat, 1);
        }

        Page<UserFootDO> footPage = userFootMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserFootDO> records = footPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageResult<>(footPage.getTotal(), Collections.emptyList());
        }

        List<Long> articleIds = records.stream()
                .map(UserFootDO::getDocumentId)
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(footPage.getTotal(), articleService.getApprovedArticlesByIds(articleIds));
    }

    /**
     * 判断当前用户是否已关注目标用户。
     */
    private boolean isFollowing(Long currentUserId, Long targetUserId) {
        UserRelationDO relation = findRelation(targetUserId, currentUserId);
        return relation != null && Objects.equals(relation.getFollowState(), FOLLOW_STATE_FOLLOWED);
    }

    /**
     * 查询用户之间的关注关系记录。
     */
    private UserRelationDO findRelation(Long targetUserId, Long followerUserId) {
        return userRelationMapper.selectOne(new LambdaQueryWrapper<UserRelationDO>()
                .eq(UserRelationDO::getUserId, targetUserId)
                .eq(UserRelationDO::getFollowUserId, followerUserId)
                .last("limit 1"));
    }

    /**
     * 发送关注通知消息。
     */
    private void sendFollowNotify(Long followerId, Long notifyUserId) {
        if (Objects.equals(followerId, notifyUserId)) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        UserFollowNotifyMessage message = UserFollowNotifyMessage.builder()
                .followerId(followerId)
                .notifyUserId(notifyUserId)
                .followerName(resolveDisplayName(followerId))
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    NOTIFY_EXCHANGE,
                    FOLLOW_NOTIFY_ROUTING_KEY,
                    message,
                    amqpMessage -> {
                        amqpMessage.getMessageProperties().setCorrelationId(messageId);
                        amqpMessage.getMessageProperties().setMessageId(messageId);
                        return amqpMessage;
                    },
                    new CorrelationData(messageId));
        } catch (Exception e) {
            log.warn("follow notify send failed, followerId={}, notifyUserId={}", followerId, notifyUserId, e);
        }
    }

    /**
     * 查询用户展示名。
     */
    private String resolveDisplayName(Long userId) {
        UserDO user = requireUser(userId);
        UserInfoDO userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfoDO>()
                .eq(UserInfoDO::getUserId, userId)
                .last("limit 1"));
        return resolveDisplayName(userId, user, userInfo);
    }

    /**
     * 从基础信息和扩展信息中解析展示名。
     */
    private String resolveDisplayName(Long userId, UserDO user, UserInfoDO userInfo) {
        if (userInfo != null && StringUtils.hasText(userInfo.getUsername())) {
            return userInfo.getUsername();
        }
        if (user != null && StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "user-" + userId;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer normalizeRole(Integer userRole) {
        return userRole == null ? 0 : userRole;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    /**
     * 校验用户存在。
     */
    private UserDO requireUser(Long userId) {
        validateUserId(userId);
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(StatusEnum.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 校验目标用户是否合法。
     */
    private void validateTargetUser(Long targetUserId, Long currentUserId, boolean failOnSelfFollow) {
        validateUserId(targetUserId);
        if (failOnSelfFollow && Objects.equals(targetUserId, currentUserId)) {
            throw new BusinessException(StatusEnum.FOLLOW_SELF_NOT_ALLOWED);
        }
        if (!Objects.equals(targetUserId, currentUserId)) {
            requireUser(targetUserId);
        }
    }

    /**
     * 校验用户 ID 参数。
     */
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    /**
     * 校验分页参数。
     */
    private void validatePage(Integer page, Integer size) {
        if (page == null || page < 1 || size == null || size < 1) {
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
    }

    private void ensureSelfAccess(Long userId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(StatusEnum.AUTH_REQUIRED);
        }
        if (!Objects.equals(currentUserId, userId)) {
            throw new BusinessException(StatusEnum.AUTH_FORBIDDEN);
        }
    }

    /**
     * 获取当前登录用户 ID。
     */
    private Long getCurrentUserId() {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        return reqInfo == null ? null : reqInfo.getUserId();
    }
}
