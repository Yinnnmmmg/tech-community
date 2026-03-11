package com.ying.tech.community.core.global;

import lombok.Data;

/**
 * 请求上下文
 * 利用 ThreadLocal 保存当前登录的用户信息
 * 这样在 Service 层的任何地方，都可以直接获取当前用户ID，不用层层传参
 */
public class ReqInfoContext {

    // 1. 定义一个 ThreadLocal，专门存 UserReqInfo
    private static final ThreadLocal<ReqInfo> TRANS_THREAD_LOCAL = new ThreadLocal<>();

    // 2. 存放用户信息
    public static void addReqInfo(ReqInfo reqInfo) {
        TRANS_THREAD_LOCAL.set(reqInfo);
    }

    // 3. 获取用户信息
    public static ReqInfo getReqInfo() {
        return TRANS_THREAD_LOCAL.get();
    }

    // 4. 清除用户信息 (非常重要！防止内存泄漏)
    public static void clear() {
        TRANS_THREAD_LOCAL.remove();
    }

    // 内部类，定义存什么东西
    @Data
    public static class ReqInfo {
        private Long userId;
        private String token;
    }
}