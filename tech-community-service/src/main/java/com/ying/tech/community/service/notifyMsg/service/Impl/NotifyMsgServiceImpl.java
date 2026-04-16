package com.ying.tech.community.service.notifyMsg.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import com.ying.tech.community.service.notifyMsg.service.NotifyMsgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内消息服务实现。
 */
@Service
@Slf4j
public class NotifyMsgServiceImpl implements NotifyMsgService {
    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    /**
     * 查询当前用户的系统消息。
     */
    @Override
    public List<String> getMySystemNotify() {
        return getMyNotifyByType(NotifyMsgConstants.Type.SYSTEM);
    }

    /**
     * 查询当前用户的关注通知消息。
     */
    @Override
    public List<String> getMyFollowNotify() {
        return getMyNotifyByType(NotifyMsgConstants.Type.FOLLOW);
    }

    /**
     * 按消息类型查询当前用户的消息内容列表。
     */
    private List<String> getMyNotifyByType(Integer type) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        QueryWrapper<NotifyMsgDO> wrapper = new QueryWrapper<NotifyMsgDO>()
                .select("msg")
                .eq("notify_user_id", userId)
                .eq("type", type)
                .orderByDesc("create_time");

        // 仅拉取 msg 字段，减少无关列映射。
        return notifyMsgMapper.selectMaps(wrapper)
                .stream()
                .map(map -> (String) map.get("msg"))
                .collect(Collectors.toList());
    }
}
