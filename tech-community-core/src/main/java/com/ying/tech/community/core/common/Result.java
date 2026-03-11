package com.ying.tech.community.core.common;

import com.ying.tech.community.core.exception.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(){
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        return result;
    }
    public static <T> Result<T> success(T data){
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }
    public static <T> Result<T> fail(){
        Result<T> result = new Result<>();
        result.code = 500;
        result.message = "fail";
        return result;
    }

    // 新增：支持传入错误码和错误信息
    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    // 新增：支持直接传入枚举
    public static <T> Result<T> fail(StatusEnum status) {
        Result<T> result = new Result<>();
        result.code = status.getCode();
        result.message = status.getMsg();
        return result;
    }
}
