package com.ying.tech.community.core.constants;

/**
 * 通知消息常量类
 */
public class NotifyMsgConstants {

    /**
     * 通知消息类型
     */
    public static class Type {
        /**
         * 默认类型
         */
        public static final Integer DEFAULT = 0;

        /**
         * 评论
         */
        public static final Integer COMMENT = 1;

        /**
         * 回复
         */
        public static final Integer REPLY = 2;

        /**
         * 点赞
         */
        public static final Integer LIKE = 3;

        /**
         * 收藏
         */
        public static final Integer FAVORITE = 4;

        /**
         * 关注
         */
        public static final Integer FOLLOW = 5;

        /**
         * 系统通知
         */
        public static final Integer SYSTEM = 6;
    }

    /**
     * 通知消息状态
     */
    public static class State {
        /**
         * 未读
         */
        public static final Integer UNREAD = 0;

        /**
         * 已读
         */
        public static final Integer READ = 1;
    }
}
