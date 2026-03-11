package com.ying.tech.community.core.exception;

import lombok.Getter;

/**
 * 业务异常
 * 当我们手动抛出这个异常时，表示这是意料之中的业务逻辑拦截
 */
@Getter
public class BusinessException extends RuntimeException {
    private int code;
    private String msg;

    // 直接传枚举，最规范
    public BusinessException(StatusEnum statusEnum) {
        super(statusEnum.getMsg());
        this.code = statusEnum.getCode();
        this.msg = statusEnum.getMsg();
    }

    // 特殊情况也可以手动传
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}