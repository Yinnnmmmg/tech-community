package com.ying.tech.community.core.exception;

import lombok.Getter;

/**
 * 业务状态码枚举。
 *
 * <p>200 表示成功，4xx 表示参数或业务拦截错误，5xx 表示系统内部错误。
 */
@Getter
public enum StatusEnum {
    SUCCESS(200, "OK"),
    UNEXPECTED_ERROR(500, "系统发生意外，请联系管理员"),
    USER_EXISTS(4001, "用户名已存在"),
    USER_NOT_FOUND(4002, "用户不存在"),
    USER_PWD_ERROR(4003, "用户名或密码错误"),
    PARAM_ILLEGAL(4004, "参数不合法"),
    PARAM_NOTNULL(4005, "参数不能为空"),
    FILE_EMPTY(4006, "上传文件不能为空"),
    FILE_TOO_LARGE(4007, "文件超出允许上传大小"),
    FILE_TYPE_NOT_ALLOWED(4008, "文件类型不允许上传"),
    ATTACHMENT_NOT_FOUND(4009, "附件不存在"),
    ATTACHMENT_ACCESS_DENIED(4010, "无权操作该附件"),
    ATTACHMENT_ALREADY_BOUND(4011, "附件已绑定到文章"),
    FOLLOW_SELF_NOT_ALLOWED(4012, "不能关注自己"),
    ARTICLE_NOT_FOUND(4013, "文章不存在"),
    ARTICLE_ACCESS_DENIED(4014, "无权操作该文章"),
    FILE_UPLOAD_FAILED(5001, "文件上传失败");

    // 业务错误码统一使用自定义编码，避免与 HTTP 状态码语义混淆。
    private final int code;
    private final String msg;

    StatusEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
