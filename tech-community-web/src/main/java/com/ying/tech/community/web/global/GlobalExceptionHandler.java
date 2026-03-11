package com.ying.tech.community.web.global;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice // 这是一个增强版 Controller，专门处理全局逻辑
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常 (BusinessException)
     * 例如：用户已存在、密码错误
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        // 返回优雅的 JSON，而不是 500 页面
        return Result.fail(e.getCode(), e.getMsg());
    }

    /**
     * 捕获系统异常 (Exception)
     * 例如：空指针、数据库连不上、代码写错了
     * 这是最后的防线
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleSystemException(Exception e) {
        log.error("系统异常: ", e); // 打印堆栈信息方便排查
        return Result.fail(StatusEnum.UNEXPECTED_ERROR);
    }

    /**
     * 使用了 @Validated 注解的  参数验证失败
     * 捕获前端传参错误
     * 例如：必填的参数为空
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));  // 改为 "; " 带空格
        log.warn("参数验证失败：{}", errorMsg);
        return Result.fail(StatusEnum.PARAM_NOTNULL.getCode(),errorMsg);
    }
}
