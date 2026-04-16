package com.ying.tech.community.service.notifyMsg.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户关注通知消息体。
 * 用于关注关系变更后向通知模块传递关注者和被关注者信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowNotifyMessage implements Serializable {
    /**
     * 关注者用户 ID。
     */
    private Long followerId;

    /**
     * 接收通知的用户 ID，也就是被关注用户。
     */
    private Long notifyUserId;

    /**
     * 关注者展示名称，用于直接生成通知文案。
     */
    private String followerName;
}
