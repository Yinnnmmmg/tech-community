package com.ying.tech.community.web.global;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("business exception: code={}, msg={}", e.getCode(), e.getMsg());
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("upload size exceeded", e);
        return Result.fail(StatusEnum.FILE_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("validation failed: {}", errorMsg);
        return Result.fail(StatusEnum.PARAM_NOTNULL.getCode(), errorMsg);
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("not login: {}", e.getMessage());
        return Result.fail(StatusEnum.AUTH_REQUIRED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public Result<Void> handlePermissionException(SaTokenException e) {
        log.warn("permission denied: {}", e.getMessage());
        return Result.fail(StatusEnum.AUTH_FORBIDDEN);
    }

    @ExceptionHandler(SaTokenException.class)
    public Result<Void> handleSaTokenException(SaTokenException e) {
        log.warn("sa-token exception: {}", e.getMessage());
        return Result.fail(StatusEnum.AUTH_REQUIRED.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleSystemException(Exception e) {
        log.error("system exception", e);
        return Result.fail(StatusEnum.UNEXPECTED_ERROR);
    }
}
