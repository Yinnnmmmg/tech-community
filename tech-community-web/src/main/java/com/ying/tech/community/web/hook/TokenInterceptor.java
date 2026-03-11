package com.ying.tech.community.web.hook;


import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.core.utils.JWTUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * 前置处理：在进入 Controller 之前执行
     * @return true=放行, false=拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头获取 Token
        String token = request.getHeader(HEADER_AUTHORIZATION);

        // 2. 如果没有 Token，先看看是不是本身就不需要登录的接口？
        // (这一步其实 WebMvcConfig 会配置 excludePathPatterns，但这里做个兜底也可以)
        // 为了简单，我们这里假设：只要配置了拦截路径的，必须有 Token
        if (!StringUtils.hasText(token)) {
            // 如果你希望没有登录也能访问某些只读接口，这里可以返回 true，但 userId 为 null
            // 这里我们做严格模式：必须登录
            log.warn("拦截请求：Header中没有Token");
            // 抛出异常，会被 GlobalExceptionHandler 捕获，返回 401/500
            throw new RuntimeException("请先登录");
        }

        // 3. 解析 Token
        Long userId = JWTUtils.getUserId(token);
        if (userId == null) {
            log.warn("拦截请求：Token非法或已过期");
            throw new RuntimeException("Token无效，请重新登录");
        }

        // 4. 将 UserID 存入 ThreadLocal
        ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
        reqInfo.setUserId(userId);
        reqInfo.setToken(token);
        ReqInfoContext.addReqInfo(reqInfo);

        // 5. 放行
        return true;
    }

    /**
     * 完成处理：请求结束后执行
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 6. 必须清除 ThreadLocal，防止内存泄漏！
        // 因为 Tomcat 的线程池是复用的，如果不清空，下个请求复用这个线程时，会读到上个用户的数据
        ReqInfoContext.clear();
    }
}