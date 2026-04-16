package com.ying.tech.community.core.constants;

/**
 * Redis key 常量定义。
 *
 * <p>统一维护文章、互动行为以及 AI 会话相关缓存前缀，避免业务侧散落硬编码。
 */
public class RedisConstants {
    /** 文章主数据缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE = "tech-community:article:";
    /** 文章详情缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE_DETAIL = "tech-community:article-detail:";
    /** 文章时间轴缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE_LIST = "tech-community:article-list:";
    /** 文章点赞集合缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE_LIKE = "tech-community:article-like:";
    /** 文章收藏集合缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE_COLLECT = "tech-community:article-collect:";
    /** 文章浏览量缓存前缀。 */
    public static final String TECH_COMMUNITY_ARTICLE_VIEW_COUNT = "tech-community:article-view-count:";

    /** AI 对话会话消息缓存前缀。 */
    public static final String TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES = "tech-community:ai:chat:session:messages:";

}
