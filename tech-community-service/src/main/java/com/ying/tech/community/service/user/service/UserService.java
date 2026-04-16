package com.ying.tech.community.service.user.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.vo.FollowActionVO;
import com.ying.tech.community.service.user.vo.FollowStatsVO;
import com.ying.tech.community.service.user.vo.UserFollowListItemVO;

/**
 * 用户领域服务。
 */
public interface UserService {
    /**
     * 根据用户名查询用户。
     */
    UserDO getByUsername(String username);

    /**
     * 用户注册。
     *
     * @param req 注册参数
     * @return 新用户 ID
     */
    Long register(UserSaveReq req);

    /**
     * 用户登录。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录 Token
     */
    String login(String username, String password);

    /**
     * 获取用户信息。
     */
    UserDO getUserInfo(Long userId);

    /**
     * 关注指定用户。
     */
    FollowActionVO followUser(Long targetUserId);

    /**
     * 取消关注指定用户。
     */
    FollowActionVO unfollowUser(Long targetUserId);

    /**
     * 获取当前用户对目标用户的关注状态。
     */
    FollowActionVO getFollowStatus(Long targetUserId);

    /**
     * 获取用户关注统计信息。
     */
    FollowStatsVO getFollowStats(Long userId);

    /**
     * 分页查询关注列表。
     */
    PageResult<UserFollowListItemVO> getFollowList(Long userId, Integer page, Integer size);

    /**
     * 分页查询粉丝列表。
     */
    PageResult<UserFollowListItemVO> getFanList(Long userId, Integer page, Integer size);
}
