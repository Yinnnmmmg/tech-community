package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.notifyMsg.service.Impl.NotifyMsgServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/Notify")
public class NotifyMsgController {
    @Autowired
    private NotifyMsgServiceImpl notifyMsgService;

    /**
     * 获取我的系统消息
     * */
    @GetMapping("/MySystemNotify")
    public Result<List<String>> getMySystemNotify(){
        List<String> mySystemNotify = notifyMsgService.getMySystemNotify();
        return Result.success(mySystemNotify);
    }
}
