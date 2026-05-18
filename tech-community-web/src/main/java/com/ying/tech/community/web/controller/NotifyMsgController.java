package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.notifyMsg.service.Impl.NotifyMsgServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息接口。
 */
@RestController
@RequestMapping("/Notify")
public class NotifyMsgController {
    @Autowired
    private NotifyMsgServiceImpl notifyMsgService;

    /**
     * 获取当前用户的系统消息。
     */
    @GetMapping("/MySystemNotify")
    public Result<List<String>> getMySystemNotify() {
        return Result.success(notifyMsgService.getMySystemNotify());
    }

    /**
     * 获取当前用户的关注通知消息。
     */
    @GetMapping("/MyFollowNotify")
    public Result<List<String>> getMyFollowNotify() {
        return Result.success(notifyMsgService.getMyFollowNotify());
    }

    /**
     * 获取当前用户未读系统通知数量。
     */
    @GetMapping("/UnreadCount")
    public Result<Long> getUnreadCount() {
        return Result.success(notifyMsgService.getSystemUnreadCount());
    }

    /**
     * 将当前用户所有未读系统通知标记为已读。
     */
    @PutMapping("/MarkRead")
    public Result<Void> markAsRead() {
        notifyMsgService.markSystemAsRead();
        return Result.success();
    }
}
