package com.ying.tech.community.service.storage.model;

/**
 * 对象存储上传结果。
 *
 * @param objectKey 对象在存储系统中的唯一键
 * @param url       对外可访问的资源地址
 */
public record StoredObject(String objectKey, String url) {
}
