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

@Service
@Slf4j
public class NotifyMsgServiceImpl implements NotifyMsgService {
    @Autowired
    private NotifyMsgMapper notifyMsgMapper;

    @Override
    public List<String> getMySystemNotify() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        QueryWrapper<NotifyMsgDO> wrapper = new QueryWrapper<NotifyMsgDO>()
                .select("msg")
                .eq("notify_user_id", userId)
                .eq("type", NotifyMsgConstants.Type.SYSTEM);
        
        // 使用 selectMaps 只获取指定字段，避免类型映射问题
        List<String> msgList = notifyMsgMapper.selectMaps(wrapper)
                .stream()
                .map(m -> (String) m.get("msg"))
                .collect(Collectors.toList());
        return msgList;
    }
}
