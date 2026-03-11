package com.ying.tech.community.core.exception;

import lombok.Getter;



/**
 * 异常状态码枚举
 * 200: 成功
 * 4xx: 前端参数问题 / 业务逻辑拦截
 * 5xx: 系统内部错误
 */
@Getter
public enum StatusEnum {
    SUCCESS(200, "OK"),
    UNEXPECTED_ERROR(500, "系统发生意外，请联系管理员"),

    // 业务错误码 (建议 4 开头，防止和 HTTP 状态码混淆)
    USER_EXISTS(4001, "用户名已存在"),
    USER_NOT_FOUND(4002, "用户不存在"),
    USER_PWD_ERROR(4003, "用户名或密码错误"),
    PARAM_ILLEGAL(4004, "参数不合法"),
    PARAM_NOTNULL(4005, "参数不能为空");

    private final int code;
    private final String msg;

    StatusEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}