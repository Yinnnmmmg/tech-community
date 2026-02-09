package com.ying.techcommunitycore.utils;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON工具类
 */
public class JsonUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 对象转JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            System.err.println("对象转JSON失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * JSON字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            System.err.println("JSON转对象失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 使用Hutool进行格式化
     */
    public static String formatJson(String json) {
        return JSONUtil.formatJsonStr(json);
    }
}