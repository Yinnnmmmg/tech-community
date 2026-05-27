package com.ying.tech.community.service.notifyMsg.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.NotifyMsgConstants;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.notifyMsg.entity.NotifyMsgDO;
import com.ying.tech.community.service.notifyMsg.repository.mapper.NotifyMsgMapper;
import com.ying.tech.community.service.notifyMsg.service.NotifyMsgService;
import com.ying.tech.community.service.notifyMsg.vo.NotifyMsgVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
    public List<NotifyMsgVO> getMySystemNotify() {
        return getMyNotifyByType(NotifyMsgConstants.Type.SYSTEM);
    }

    /**
     * 查询当前用户的关注通知消息。
     */
    @Override
    public List<NotifyMsgVO> getMyFollowNotify() {
        return getMyNotifyByType(NotifyMsgConstants.Type.FOLLOW);
    }

    /**
     * 获取当前用户未读系统通知数量。
     */
    @Override
    public long getSystemUnreadCount() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        return notifyMsgMapper.selectCount(
                new QueryWrapper<NotifyMsgDO>()
                        .eq("notify_user_id", userId)
                        .eq("type", NotifyMsgConstants.Type.SYSTEM)
                        .eq("state", NotifyMsgConstants.State.UNREAD)
        );
    }

    /**
     * 将当前用户所有未读系统通知标记为已读。
     */
    @Override
    public void markSystemAsRead() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        notifyMsgMapper.update(
                NotifyMsgDO.builder().state(NotifyMsgConstants.State.READ).build(),
                new QueryWrapper<NotifyMsgDO>()
                        .eq("notify_user_id", userId)
                        .eq("type", NotifyMsgConstants.Type.SYSTEM)
                        .eq("state", NotifyMsgConstants.State.UNREAD)
        );
    }

    /**
     * 按消息类型查询当前用户的消息列表（含时间）。
     */
    private List<NotifyMsgVO> getMyNotifyByType(Integer type) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        QueryWrapper<NotifyMsgDO> wrapper = new QueryWrapper<NotifyMsgDO>()
                .select("msg", "create_time")
                .eq("notify_user_id", userId)
                .eq("type", type)
                .orderByDesc("create_time");

        return notifyMsgMapper.selectMaps(wrapper)
                .stream()
                .map(map -> {
                    Timestamp ts = (Timestamp) map.get("create_time");
                    return NotifyMsgVO.builder()
                        .msg((String) map.get("msg"))
                        .createTime(ts != null ? ts.toLocalDateTime() : null)
                        .build();
                })
                .collect(Collectors.toList());
    }
}
