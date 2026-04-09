package com.ying.tech.community.service.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class ArticleDataSyncTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;

    public ArticleDataSyncTask(StringRedisTemplate stringRedisTemplate, ArticleMapper articleMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.articleMapper = articleMapper;
    }

    @Scheduled(cron = "0 0 0/1 * * ?")
    public void syncArticleDataToMYSQL() {
        log.info("开始执行 Redis 到 MySQL 的文章数据同步任务...");
        syncViewCount();
        log.info("文章数据同步任务执行完毕");
    }

    private void syncViewCount() {
        String viewKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT;
        Set<String> keys = stringRedisTemplate.keys(viewKey + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                String viewCount = stringRedisTemplate.opsForValue().get(key);
                if (viewCount == null) {
                    continue;
                }

                long parsedViewCount = Long.parseLong(viewCount);
                UpdateWrapper<ArticleDO> articleDOUpdateWrapper = new UpdateWrapper<ArticleDO>()
                        .set("view_count", parsedViewCount)
                        .eq("id", articleId);
                articleMapper.update(null, articleDOUpdateWrapper);
            } catch (Exception e) {
                log.error("文章数据同步任务执行异常，key: {}, error: {}", key, e.getMessage());
            }
        }
    }
}
