package com.ying.tech.community.service.user.service;

import com.ying.tech.community.core.common.PageResult;
import com.ying.tech.community.service.article.vo.ArticleListVO;
import com.ying.tech.community.service.user.entity.UserDO;
import com.ying.tech.community.service.user.req.ChangePasswordReq;
import com.ying.tech.community.service.user.req.PhoneLoginReq;
import com.ying.tech.community.service.user.req.SendSmsReq;
import com.ying.tech.community.service.user.req.UserProfileUpdateReq;
import com.ying.tech.community.service.user.req.UserSaveReq;
import com.ying.tech.community.service.user.vo.FollowActionVO;
import com.ying.tech.community.service.user.vo.FollowStatsVO;
import com.ying.tech.community.service.user.vo.UserCurrentVO;
import com.ying.tech.community.service.user.vo.UserFollowListItemVO;
import com.ying.tech.community.service.user.vo.UserProfileVO;

public interface UserService {
    UserDO getByUsername(String username);

    UserDO getByPhone(String phone);

    Long register(UserSaveReq req);

    String login(String username, String password);

    String loginByPhone(String phone, String password, String smsCode);

    void sendSmsCode(String phone);

    UserDO getUserInfo(Long userId);

    UserCurrentVO getCurrentUser(Long userId);

    UserProfileVO getUserProfile(Long userId);

    void updateCurrentUserProfile(Long userId, UserProfileUpdateReq req);

    void changePassword(Long userId, ChangePasswordReq req);

    PageResult<ArticleListVO> getUserArticlePage(Long userId, Integer page, Integer size);

    PageResult<ArticleListVO> getUserCollectionArticlePage(Long userId, Integer page, Integer size);

    PageResult<ArticleListVO> getUserLikeArticlePage(Long userId, Integer page, Integer size);

    FollowActionVO followUser(Long targetUserId);

    FollowActionVO unfollowUser(Long targetUserId);

    FollowActionVO getFollowStatus(Long targetUserId);

    FollowStatsVO getFollowStats(Long userId);

    PageResult<UserFollowListItemVO> getFollowList(Long userId, Integer page, Integer size);

    PageResult<UserFollowListItemVO> getFanList(Long userId, Integer page, Integer size);
}
