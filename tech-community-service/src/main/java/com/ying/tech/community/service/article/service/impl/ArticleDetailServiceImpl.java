package com.ying.tech.community.service.article.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ying.tech.community.core.constants.ArticleStatusConstants;
import com.ying.tech.community.core.constants.RedisConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.article.entity.ArticleDO;
import com.ying.tech.community.service.article.entity.ArticleDetailDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleDetailMapper;
import com.ying.tech.community.service.article.repository.mapper.ArticleMapper;
import com.ying.tech.community.service.article.service.ArticleDetailService;
import com.ying.tech.community.service.article.vo.ArticleDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class ArticleDetailServiceImpl implements ArticleDetailService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 鍒嗘閿佹暟缁勶細鍥哄畾 256 涓攣锛岄€氳繃 articleId 鍙栨ā閫夐攣锛屽交搴曢伩鍏嶅唴瀛樻硠婕?
    private static final int LOCK_SEGMENT_COUNT = 256;
    private final Lock[] segmentLocks;

    public ArticleDetailServiceImpl() {
        segmentLocks = new Lock[LOCK_SEGMENT_COUNT];
        for (int i = 0; i < LOCK_SEGMENT_COUNT; i++) {
            segmentLocks[i] = new ReentrantLock();
        }
    }

    /**
     * 鏍规嵁鏂囩珷id鑾峰彇鏂囩珷璇︽儏
     *
     * */
    @Override
    public ArticleDetailVO getArticleDetailById(Long articleId) {
        if (!isArticleApproved(articleId)) {
            log.warn("article not approved, articleId: {}", articleId);
            throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
        }
        //鎷兼帴redis鐨刱ey
        String articleDetailKey = RedisConstants.TECH_COMMUNITY_ARTICLE_DETAIL + articleId;
        //鏌edis
        ArticleDetailDO articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
        //瀛樺湪锛岃繑鍥?
        if(articleDetailDO != null){
            // 銆愰槻绌块€忛棴鐜€戯細鍒ゆ柇鎷垮埌鐨勬槸涓嶆槸鎴戜滑涓轰簡闃茬┛閫忕壒鎰忓鍏ョ殑鈥滅┖瀵硅薄鈥?
            if (articleDetailDO.getId() == null) {
                log.warn("瑙﹀彂缂撳瓨绌块€忛槻寰★紝鐩存帴鎷︽埅闈炴硶 articleId: {}", articleId);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL); // 鎴栬€?return null
            }
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
            //姣忚皟鐢ㄤ竴娆★紝娴忚娆℃暟鍔?
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        }

        //涓嶅瓨鍦紝鏌ユ暟鎹簱
        //灏濊瘯鑾峰彇閿?
        Lock lock = getLock(articleId);
        lock.lock();
        try{
            //鍏堢湅鏄惁鏈夌紦瀛橈紝鍦ㄨ繘鍏ュ悗鐪嬪厛鍓嶆湁娌℃湁绾跨▼閲嶅缓浜嗙紦瀛?
            articleDetailDO = (ArticleDetailDO)redisTemplate.opsForValue().get(articleDetailKey);
            if(articleDetailDO != null){
                // 銆愰槻绌块€忛棴鐜€戯細鍒ゆ柇鎷垮埌鐨勬槸涓嶆槸鎴戜滑涓轰簡闃茬┛閫忕壒鎰忓鍏ョ殑鈥滅┖瀵硅薄鈥?
                if (articleDetailDO.getId() == null) {
                    log.warn("瑙﹀彂缂撳瓨绌块€忛槻寰★紝鐩存帴鎷︽埅闈炴硶 articleId: {}", articleId);
                    throw new BusinessException(StatusEnum.PARAM_ILLEGAL); // 鎴栬€?return null
                }
                ArticleDetailVO articleDetailVO = new ArticleDetailVO();
                BeanUtil.copyProperties(articleDetailDO,articleDetailVO);
                //姣忚皟鐢ㄤ竴娆★紝娴忚娆℃暟鍔?
                safeIncrementViewCount(articleId);
                return articleDetailVO;
            }
            //绗竴涓嚎绋嬭幏鍙栭攣鎴愬姛锛屽垯杩涜鏁版嵁搴撴煡璇?
            Long articleDetailId = articleDetailMapper.getArticleDetailIdById(articleId);
            if(articleDetailId != null){
                articleDetailDO = articleDetailMapper.selectById(articleDetailId);
            }
            if (articleDetailDO == null) {
                log.warn("鏂囩珷璇︽儏涓嶅瓨鍦紝articleId: {}", articleId);
                //缂撳瓨绌哄璞★紝闃叉缂撳瓨绌块€?
                redisTemplate.opsForValue()
                        .set(articleDetailKey,
                                new ArticleDetailDO(), 5, TimeUnit.MINUTES);
                throw new BusinessException(StatusEnum.PARAM_ILLEGAL);
            }
            ArticleDetailVO articleDetailVO = new ArticleDetailVO();
            BeanUtil.copyProperties(articleDetailDO, articleDetailVO);
            //閲嶅缓缂撳瓨锛岃缃繃鏈熸椂闂达細鍩虹1灏忔椂 + 0~10鍒嗛挓闅忔満娉㈠姩锛岄槻寰＄紦瀛橀洩宕?
            long baseMinutes = 60; // 1灏忔椂
            long randomMinutes = ThreadLocalRandom.current().nextLong(0, 11); // 0~10鍒嗛挓闅忔満鏁?
            long expireMinutes = baseMinutes + randomMinutes;
            redisTemplate.opsForValue().set(articleDetailKey, articleDetailDO, expireMinutes, TimeUnit.MINUTES);
            //姣忚皟鐢ㄤ竴娆★紝娴忚娆℃暟鍔?
            safeIncrementViewCount(articleId);
            return articleDetailVO;
        } finally {
            lock.unlock();
        }
    }


    /**
     * 瀹夊叏鍦板鍔犳枃绔犻槄璇婚噺锛岄伩鍏?Redis 涓?key 涓嶅瓨鍦ㄦ垨绫诲瀷閿欒
     */
    private void safeIncrementViewCount(Long articleId) {
        // 銆愭牳蹇冨厹搴曢€昏緫銆戯細妫€鏌?Redis 涓槸鍚︽湁杩欎釜闃呰閲?Key
        String viewCountKey = RedisConstants.TECH_COMMUNITY_ARTICLE_VIEW_COUNT + articleId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(viewCountKey))) {
            // 1. 濡傛灉娌℃湁锛屽幓 MySQL 鏌ュ嚭鐪熷疄鐨勫巻鍙查槄璇婚噺
            ArticleDO article = articleMapper.selectById(articleId);
            Long dbViewCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;

            // 2. 灏嗙湡瀹炴暟鎹鍏?Redis銆?
            // 銆愭灦鏋勭粏鑺傘€戯細浣跨敤 setIfAbsent 鑰屼笉鏄?set銆?
            // 濡傛灉鏋佺鎯呭喌涓嬫湁100涓汉鍚屾椂鍦≧edis娌℃暟鎹椂鐐硅繘鏂囩珷锛屽彧鏈変竴涓嚎绋嬭兘璁剧疆鎴愬姛锛屽叾浠栫嚎绋嬩細琚尅浣忥紝闃叉鏃ф暟鎹鐩栥€?
            // 鍚屾椂椤烘墜璁剧疆 30 澶╃殑杩囨湡鏃堕棿锛岄槻姝㈠喎鏁版嵁姘歌繙鍗犵敤鍐呭瓨銆?
            stringRedisTemplate.opsForValue().setIfAbsent(viewCountKey, String.valueOf(dbViewCount), 30, TimeUnit.DAYS);
        }
        try {
            stringRedisTemplate.opsForValue().increment(viewCountKey);
            // 鍔ㄦ€佺画鏈燂細姣忔闃呰閲忓鍔犲悗鍒锋柊杩囨湡鏃堕棿锛岄暱杩囨湡鏃堕棿 30 澶?
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("increment澶辫触锛屽洖婧怐B: {}", viewCountKey, e);

            // 1. 浠庢暟鎹簱璇诲彇鐪熷疄鍊?
            QueryWrapper<ArticleDO> articleWrapper = new QueryWrapper<ArticleDO>()
                    .select("view_count")
                    .eq("id", articleId);
            ArticleDO article = articleMapper.selectOne(articleWrapper);
            long dbCount = (article != null && article.getViewCount() != null) ? article.getViewCount() : 0L;
            // 2. 鍥炲～Redis锛堝繀椤荤敤 String锛屼繚璇?Redis INCR 鍙互姝ｅ父鎵ц锛?
            stringRedisTemplate.opsForValue().set(viewCountKey, String.valueOf(dbCount));

            // 3. 鍐嶈嚜澧?
            stringRedisTemplate.opsForValue().increment(viewCountKey);

            // 4. 璁剧疆杩囨湡鏃堕棿
            stringRedisTemplate.expire(viewCountKey, 30, TimeUnit.DAYS);
        }
    }


    private boolean isArticleApproved(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        return article != null && java.util.Objects.equals(article.getStatus(), ArticleStatusConstants.APPROVED);
    }

    /**
     * 鑾峰彇閿佸璞★紙鍒嗘閿侊紝鍥哄畾鍐呭瓨鍗犵敤锛?
     */
    private Lock getLock(Long articleId) {
        int index = (int)(articleId % LOCK_SEGMENT_COUNT);
        if (index < 0) index += LOCK_SEGMENT_COUNT;
        return segmentLocks[index];
    }
}
