package com.ying.tech.community.service.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 客户端配置。
 * 负责把阿里云 OSS SDK 客户端注册为 Spring Bean，供存储服务统一复用。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssClientConfiguration {

    /**
     * 创建 OSS 客户端实例。
     * 在容器销毁时自动调用 shutdown 释放底层连接资源。
     *
     * @param ossProperties OSS 连接配置
     * @return OSS 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }
}
