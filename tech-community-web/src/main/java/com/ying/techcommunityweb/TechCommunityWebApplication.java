package com.ying.techcommunityweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan("com.ying.tech.community") // 扫描所有子模块的 Bean
@MapperScan({"com.ying.tech.community.service.**.repository.mapper"}) // 扫描所有模块的 Mapper
public class TechCommunityWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechCommunityWebApplication.class, args);
    }

}
