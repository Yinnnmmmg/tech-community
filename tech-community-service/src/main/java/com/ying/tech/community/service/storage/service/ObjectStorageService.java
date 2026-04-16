package com.ying.tech.community.service.storage.service;

import com.ying.tech.community.service.storage.model.StoredObject;

import java.io.InputStream;

/**
 * 对象存储抽象接口。
 * 用于屏蔽具体厂商实现，业务侧只依赖统一的上传与删除能力。
 */
public interface ObjectStorageService {
    /**
     * 上传对象到存储服务。
     *
     * @param objectKey     对象键
     * @param inputStream   文件输入流
     * @param contentLength 文件字节数
     * @param contentType   MIME 类型
     * @return 上传完成后的对象信息
     */
    StoredObject upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    /**
     * 删除指定对象。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);
}
