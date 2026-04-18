# 用户关注业务说明

## 1. 功能概述

本次在项目中新增了用户关注业务，覆盖以下能力：

- 关注用户
- 取消关注
- 查询当前登录用户对目标用户的关注状态
- 查询任意用户的关注数与粉丝数
- 分页查询任意用户的关注列表与粉丝列表
- 关注成功后通过 RabbitMQ 异步写入站内关注通知
- 查询当前登录用户的关注通知

## 2. 数据模型假设

代码按现有数据库表 `user_relation` 实现，字段语义如下：

| 字段 | 含义 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 被关注的用户 ID |
| `follow_user_id` | 关注者 / 粉丝用户 ID |
| `follow_state` | 关注状态 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

`follow_state` 按当前实现约定：

- `1`：已关注
- `2`：已取关

仓库内 `.idea` 数据源元数据表明，`user_relation` 已存在唯一索引：

- `uk_user_follow(user_id, follow_user_id)`

这也是本次实现幂等更新的前提。

## 3. 接口清单

### 3.1 用户关注接口

- `POST /user/follow?targetUserId={id}`
  - 关注目标用户
- `POST /user/unfollow?targetUserId={id}`
  - 取消关注目标用户
- `GET /user/follow/status?targetUserId={id}`
  - 查询当前登录用户是否已关注目标用户

返回模型 `FollowActionVO`：

```json
{
  "targetUserId": 10002,
  "followed": true
}
```

### 3.2 关注统计接口

- `GET /user/{userId}/follow/stats`

返回模型 `FollowStatsVO`：

```json
{
  "followCount": 12,
  "fanCount": 38,
  "followed": false
}
```

说明：

- `followCount`：该用户关注了多少人
- `fanCount`：有多少人关注该用户
- `followed`：当前登录用户是否关注了该 `userId`

### 3.3 关注 / 粉丝列表接口

- `GET /user/{userId}/follows?page=1&size=10`
- `GET /user/{userId}/fans?page=1&size=10`

列表项模型 `UserFollowListItemVO`：

```json
{
  "userId": 10003,
  "username": "alice",
  "photo": "https://...",
  "position": "Java Engineer",
  "company": "Tech Community",
  "profile": "focus on backend",
  "followed": true
}
```

说明：

- `followed` 表示当前登录用户是否已关注列表项用户
- 用户展示信息优先取 `user_info.username`，缺失时回退到 `user.username`

### 3.4 关注通知接口

- `GET /Notify/MyFollowNotify`

返回当前登录用户收到的关注通知文案列表。

## 4. 业务规则

- 不能关注自己
- 目标用户不存在时返回业务异常
- 重复关注幂等成功，不重复插入关系
- 重复取关幂等成功
- 已取关关系重新关注时，复用原关系行并更新 `follow_state`
- 关注列表与粉丝列表均按 `update_time desc, id desc` 分页

## 5. 异步通知链路

### 5.1 发送时机

仅在“状态从未关注 / 已取关 -> 已关注”时发送关注通知。

以下情况不会重复发通知：

- 已经处于关注状态，再次点击关注
- 取消关注

### 5.2 MQ 配置

新增队列与路由：

- Exchange: `notify.direct`
- Routing Key: `user.follow.notify`
- Queue: `user.follow.notify.queue`
- DLQ: `user.follow.notify.dlq`

### 5.3 消息体

`UserFollowNotifyMessage`

```json
{
  "followerId": 10010,
  "notifyUserId": 10002,
  "followerName": "bob"
}
```

### 5.4 通知落库

消费者会向 `notify_msg` 写入：

- `related_id = followerId`
- `notify_user_id = notifyUserId`
- `operate_user_id = followerId`
- `type = FOLLOW`
- `state = UNREAD`
- `msg = "{followerName}关注了你"`

## 6. 幂等与异常处理

### 6.1 关系幂等

- 依赖 `user_relation(user_id, follow_user_id)` 唯一索引
- 插入冲突时回查关系行并转为更新

### 6.2 通知幂等

消费者优先使用 AMQP `messageId` 做 Redis 幂等拦截。

当 `messageId` 缺失时，退化为业务键：

- `followerId:notifyUserId`

### 6.3 异常策略

- 关注关系写库失败：接口直接失败
- 关注关系已成功，但 MQ 发送失败：记录日志，不回滚关注结果
- 通知消费失败：抛异常，由现有手动 ACK + 重试 / DLQ 机制处理

## 7. 主要代码落点

- `service.user.service.impl.UserServiceImpl`
- `web.controller.UserController`
- `web.config.RabbitMQConfig`
- `service.notifyMsg.consumer.UserFollowNotifyConsumer`
- `service.notifyMsg.service.Impl.NotifyMsgServiceImpl`

## 8. 建议验证项

- 首次关注成功
- 重复关注不重复插入
- 取关成功
- 重复取关幂等
- 重新关注复用旧关系
- 关注自己返回业务异常
- 查询关注状态正确
- 关注 / 粉丝列表分页正确
- 关注成功后 `notify_msg` 产生 `FOLLOW` 类型通知
- `/Notify/MyFollowNotify` 能读到关注通知
