package com.ying.tech.community.service.notifyMsg.service;

import java.util.List;

/**
 * 站内消息服务。
 */
public interface NotifyMsgService {
    /**
     * 获取当前用户的系统消息。
     */
    List<String> getMySystemNotify();

    /**
     * 获取当前用户的关注通知消息。
     */
    List<String> getMyFollowNotify();
}
