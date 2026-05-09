package com.ying.tech.community.web.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.web.hook.SaTokenUserContextInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private SaTokenUserContextInterceptor saTokenUserContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SaInterceptor saInterceptor = new SaInterceptor(handle -> StpUtil.checkLogin())
                .isAnnotation(true);
        HandlerInterceptor requestOnlySaInterceptor = new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (request.getDispatcherType() != DispatcherType.REQUEST) {
                    return true;
                }
                return saInterceptor.preHandle(request, response, handler);
            }
        };

        registry.addInterceptor(requestOnlySaInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/user/*/profile")
                .excludePathPatterns("/user/*/articles")
                .excludePathPatterns("/article/categories")
                .excludePathPatterns("/article/search")
                .excludePathPatterns("/article/list")
                .excludePathPatterns("/article/detail/{articleId}")
                .excludePathPatterns("/ai/chat")
                .excludePathPatterns("/covers/**")
                .excludePathPatterns("/comment/article/{articleId}/list")
                .excludePathPatterns("/comment/{commentId}/replies");

        registry.addInterceptor(saTokenUserContextInterceptor)
                .addPathPatterns("/user/**")
                .addPathPatterns("/article/**")
                .addPathPatterns("/Notify/**")
                .addPathPatterns("/ai/**")
                .addPathPatterns("/comment/**")
                .addPathPatterns("/admin/**");
    }
}
