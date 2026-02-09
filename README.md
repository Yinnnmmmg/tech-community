# Tech Community 项目

这是一个基于Spring Boot的多模块技术社区项目。

## 项目结构

```
tech-community/
├── tech-community-core/      # 核心工具模块
├── tech-community-service/   # 业务服务模块
└── tech-community-web/       # Web接口模块
```

## 模块说明

### tech-community-core (核心工具模块)
- 包含通用工具类和基础组件
- 不依赖Spring Boot
- 提供JSON处理、工具方法等

### tech-community-service (业务服务模块)
- 依赖core模块
- 包含业务逻辑和服务层
- 集成MyBatis-Plus、MySQL等

### tech-community-web (Web接口模块)
- 依赖service模块
- 提供RESTful API接口
- Spring Boot Web应用入口

## 构建和运行

```bash
# 清理并编译所有模块
mvn clean compile

# 运行所有测试
mvn test

# 打包项目
mvn package

# 运行Web应用
mvn spring-boot:run -pl tech-community-web
```

## 技术栈

- Java 17
- Spring Boot 3.2.0
- MyBatis-Plus 3.5.5
- MySQL 8.0.33
- Hutool 5.8.25
- Lombok
- Jackson

## 依赖关系

```
tech-community-web
    ↓ 依赖
tech-community-service
    ↓ 依赖
tech-community-core
```