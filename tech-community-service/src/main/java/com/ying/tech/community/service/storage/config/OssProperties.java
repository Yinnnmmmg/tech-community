package com.ying.tech.community.service.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS 相关配置项。
 * 对应 application.yml 中的 app.oss 前缀，用于集中管理上传约束和访问参数。
 */
@Data
@ConfigurationProperties(prefix = "app.oss")
public class OssProperties {
    /**
     * OSS 访问端点，例如 oss-cn-hangzhou.aliyuncs.com。
     */
    private String endpoint;

    /**
     * 上传所使用的 bucket 名称。
     */
    private String bucket;

    /**
     * 阿里云 AccessKeyId。
     */
    private String accessKeyId;

    /**
     * 阿里云 AccessKeySecret。
     */
    private String accessKeySecret;

    /**
     * 对外访问文件时优先使用的公共域名或基础 URL。
     * 如果未配置，则由 endpoint 和 bucket 自动拼接默认访问地址。
     */
    private String publicBaseUrl;

    /**
     * 单个文件允许上传的最大体积，默认 100MB。
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(100);

    /**
     * 允许的 MIME 类型白名单。
     * 留空表示不额外限制 content-type。
     */
    private List<String> allowedContentTypes = new ArrayList<>();

    /**
     * 允许的文件扩展名白名单，统一使用小写进行比较。
     */
    private List<String> allowedExtensions = new ArrayList<>();
}
