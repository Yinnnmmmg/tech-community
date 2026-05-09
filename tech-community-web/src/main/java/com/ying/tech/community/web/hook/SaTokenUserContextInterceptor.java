package com.ying.tech.community.web.hook;

import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.core.global.ReqInfoContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SaTokenUserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            ReqInfoContext.clear();
            return true;
        }

        try {
            if (!StpUtil.isLogin()) {
                ReqInfoContext.clear();
                return true;
            }

            ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
            reqInfo.setUserId(Long.parseLong(StpUtil.getLoginId().toString()));
            reqInfo.setToken(StpUtil.getTokenValue());
            ReqInfoContext.addReqInfo(reqInfo);
        } catch (Exception e) {
            ReqInfoContext.clear();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ReqInfoContext.clear();
    }
}
