package com.ying.tech.community.service.storage.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.ying.tech.community.service.storage.config.OssProperties;
import com.ying.tech.community.service.storage.model.StoredObject;
import com.ying.tech.community.service.storage.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 基于阿里云 OSS 的对象存储实现。
 * 负责上传文件、删除文件，并把对象键转换成业务可直接返回给前端的访问地址。
 */
@Service
public class AliyunOssObjectStorageService implements ObjectStorageService {
    private final OSS ossClient;
    private final OssProperties ossProperties;

    public AliyunOssObjectStorageService(OSS ossClient, OssProperties ossProperties) {
        this.ossClient = ossClient;
        this.ossProperties = ossProperties;
    }

    @Override
    public StoredObject upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        // 元数据会直接影响浏览器下载行为和对象内容识别，因此上传时一并写入。
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        metadata.setContentType(contentType);
        ossClient.putObject(ossProperties.getBucket(), objectKey, inputStream, metadata);
        return new StoredObject(objectKey, buildPublicUrl(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        ossClient.deleteObject(ossProperties.getBucket(), objectKey);
    }

    private String buildPublicUrl(String objectKey) {
        // 优先使用显式配置的对外域名，便于接入 CDN、自定义域名或网关层。
        String baseUrl = StringUtils.hasText(ossProperties.getPublicBaseUrl())
                ? ossProperties.getPublicBaseUrl()
                : buildDefaultBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/" + objectKey;
    }

    private String buildDefaultBaseUrl() {
        String endpoint = ossProperties.getEndpoint();
        // 兼容 endpoint 已经携带协议头和只配置裸域名两种场景。
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint + "/" + ossProperties.getBucket();
        }
        return "https://" + ossProperties.getBucket() + "." + endpoint;
    }
}
