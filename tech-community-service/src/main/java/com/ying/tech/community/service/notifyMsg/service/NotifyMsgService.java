package com.ying.tech.community.service.notifyMsg.service;

import com.ying.tech.community.service.notifyMsg.vo.NotifyMsgVO;

import java.util.List;

/**
 * 站内消息服务。
 */
public interface NotifyMsgService {
    /**
     * 获取当前用户的系统消息。
     */
    List<NotifyMsgVO> getMySystemNotify();

    /**
     * 获取当前用户的关注通知消息。
     */
    List<NotifyMsgVO> getMyFollowNotify();

    /**
     * 获取当前用户未读系统通知数量。
     */
    long getSystemUnreadCount();

    /**
     * 将当前用户所有未读系统通知标记为已读。
     */
    void markSystemAsRead();
}
