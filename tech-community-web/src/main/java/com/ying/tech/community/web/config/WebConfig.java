package com.ying.tech.community.web.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.ying.tech.community.web.hook.SaTokenUserContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * <p>
 * 用于配置 Spring MVC 的拦截器，实现以下功能：
 * 1. Sa-Token 用户上下文拦截器 - 将登录用户信息存入 ThreadLocal
 * 2. Sa-Token 鉴权拦截器 - 基于注解进行权限校验（如 @SaCheckLogin）
 *
 * @author Tech Community
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Sa-Token 用户上下文拦截器
     * 用于从 Token 中获取用户信息并存储到 ThreadLocal，方便业务层使用
     */
    @Autowired
    private SaTokenUserContextInterceptor saTokenUserContextInterceptor;

    /**
     * 注册拦截器
     * <p>
     * 配置两个拦截器，按顺序执行：
     * 1. SaTokenUserContextInterceptor: 先提取用户信息到上下文
     * 2. SaInterceptor: 后进行权限校验
     * <p>
     * 拦截路径包括：
     * - /user/**   : 用户相关接口
     * - /article/**: 文章相关接口
     * - /Notify/** : 通知相关接口
     * - /ai/**     : AI 相关接口
     * - /comment/**: 评论相关接口
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 用户上下文拦截器
        // 作用：从请求中提取 Token，解析用户信息并存入 ThreadLocal
        // 先保留，后期获取用户改成用 StpUtil.getLoginId()
        registry.addInterceptor(saTokenUserContextInterceptor)
                .addPathPatterns("/user/**")
                .addPathPatterns("/article/**")
                .addPathPatterns("/Notify/**")
                .addPathPatterns("/ai/**")
                .addPathPatterns("/comment/**");

        // 注册 Sa-Token 鉴权拦截器
        // isAnnotation(true): 启用注解鉴权模式，支持 @SaCheckLogin、@SaCheckRole、@SaCheckPermission 等注解
        // 作用：根据 Controller 方法上的注解进行权限校验
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                //添加开放的路径
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/article/search")
                .excludePathPatterns("/article/list")
                .excludePathPatterns("/article/detail/{articleId}")
                .excludePathPatterns("/comment/article/{articleId}/list")
                .excludePathPatterns("/comment/{commentId}/replies");
    }
}
