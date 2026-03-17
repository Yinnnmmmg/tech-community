package com.ying.tech.community.service.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class ArticleDataSyncTask {

    private final RedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;

    public ArticleDataSyncTask(RedisTemplate redisTemplate, ArticleMapper articleMapper) {
        this.redisTemplate = redisTemplate;
        this.articleMapper = articleMapper;
    }

    @Scheduled(cron = "0 0 0/1 * * ?")//每1小时执行一次把redis的点赞量和浏览量同步到mysql
    public void syncArticleDataToMYSQL() {
        log.info("开始执行 Redis 到 MySQL 的文章数据同步任务...");
        syncLikeCount();
        syncViewCount();
        log.info("文章数据同步任务执行完毕");
    }

    private void syncViewCount(){
        String viewKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT;
        //模糊匹配所有key
        Set<String> keys = redisTemplate.keys(viewKey + "*");
        if(keys == null || keys.isEmpty()){
            return;
        }
        for (String key : keys) {
            try{
                //获取阅读量
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                Object viewCountObj = redisTemplate.opsForValue().get(key);
                if(viewCountObj != null){
                    //写入mysql
                    long viewCount = Long.parseLong(viewCountObj.toString());
                    UpdateWrapper<ArticleDO> articleDOUpdateWrapper = new UpdateWrapper<ArticleDO>()
                            .set("view_count",viewCount)
                            .eq("id",articleId);
                    articleMapper.update(null,articleDOUpdateWrapper);
                }
            } catch (Exception e){
                log.error("文章数据同步任务执行异常，key: {}, 错误：{}", key, e.getMessage());
            }
        }

    }

    private void syncLikeCount(){
        String likeKey = RedisConstants.TECH_COMMUNITY_ARTICLE_LIKE;
        //模糊匹配所有 key
        Set<String> keys = redisTemplate.keys(likeKey + "*");
        if(keys == null || keys.isEmpty()){
            return;
        }
        for (String key : keys) {
            try{
                //获取点赞量
                String articleIdStr = key.substring(key.lastIndexOf(":") + 1);
                Long articleId = Long.parseLong(articleIdStr);
                Long likeCount = redisTemplate.opsForSet().size(key);
                if(likeCount != null){
                    //写入 mysql
                    UpdateWrapper<ArticleDO> articleDOUpdateWrapper = new UpdateWrapper<ArticleDO>()
                            .set("like_count",likeCount)
                            .eq("id",articleId);
                    articleMapper.update(null,articleDOUpdateWrapper);
                }
            } catch (Exception e){
                log.error("文章点赞数据同步任务执行异常，key: {}, 错误：{}", key, e.getMessage());
            }
        }
    }
}
