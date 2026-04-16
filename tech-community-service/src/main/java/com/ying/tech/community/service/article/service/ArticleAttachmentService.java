package com.ying.tech.community.service.article.service;

import com.ying.tech.community.service.article.entity.ArticleAttachmentDO;
import com.ying.tech.community.service.article.vo.ArticleAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文章附件服务。
 * 负责附件上传、文章绑定、替换、释放以及列表查询等附件生命周期管理。
 */
public interface ArticleAttachmentService {
    /**
     * 上传单个附件并保存元数据。
     *
     * @param file 上传文件
     * @return 附件展示信息
     */
    ArticleAttachmentVO uploadAttachment(MultipartFile file);

    /**
     * 把一组附件绑定到指定文章。
     *
     * @param articleId      文章 ID
     * @param userId         操作者 ID
     * @param attachmentIds  附件 ID 列表
     * @return 按传入顺序返回绑定后的附件列表
     */
    List<ArticleAttachmentDO> bindAttachmentsToArticle(Long articleId, Long userId, List<Long> attachmentIds);

    /**
     * 替换文章当前绑定的附件集合。
     *
     * @param articleId      文章 ID
     * @param userId         操作者 ID
     * @param attachmentIds  目标附件 ID 列表
     * @return 替换后实际绑定的附件列表
     */
    List<ArticleAttachmentDO> replaceAttachmentsOnArticle(Long articleId, Long userId, List<Long> attachmentIds);

    /**
     * 释放文章已绑定的全部附件。
     *
     * @param articleId 文章 ID
     */
    void releaseAttachmentsOnArticle(Long articleId);

    /**
     * 统计多篇文章各自绑定的附件数量。
     *
     * @param articleIds 文章 ID 集合
     * @return key 为文章 ID，value 为附件数
     */
    Map<Long, Long> countBoundAttachments(List<Long> articleIds);

    /**
     * 查询某篇文章当前已绑定的附件列表。
     *
     * @param articleId 文章 ID
     * @return 附件展示信息列表
     */
    List<ArticleAttachmentVO> listBoundAttachments(Long articleId);
}
