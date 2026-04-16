package com.ying.tech.community.web.controller;

import com.ying.tech.community.core.common.Result;
import com.ying.tech.community.service.article.service.ArticleAttachmentService;
import com.ying.tech.community.service.article.vo.ArticleAttachmentVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文章附件控制器。
 * 对外提供附件上传接口，供发文或编辑文章时先上传文件再绑定到文章。
 */
@RestController
@RequestMapping("/article/attachment")
public class ArticleAttachmentController {
    private final ArticleAttachmentService articleAttachmentService;

    public ArticleAttachmentController(ArticleAttachmentService articleAttachmentService) {
        this.articleAttachmentService = articleAttachmentService;
    }

    /**
     * 上传单个文章附件。
     *
     * @param file 前端上传的文件
     * @return 上传后的附件信息
     */
    @PostMapping("/upload")
    public Result<ArticleAttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file) {
        return Result.success(articleAttachmentService.uploadAttachment(file));
    }
}
