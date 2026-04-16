package com.ying.tech.community.service.article.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ying.tech.community.core.constants.ArticleAttachmentStatusConstants;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.core.global.ReqInfoContext;
import com.ying.tech.community.service.article.entity.ArticleAttachmentDO;
import com.ying.tech.community.service.article.repository.mapper.ArticleAttachmentMapper;
import com.ying.tech.community.service.article.service.ArticleAttachmentService;
import com.ying.tech.community.service.article.vo.ArticleAttachmentVO;
import com.ying.tech.community.service.storage.config.OssProperties;
import com.ying.tech.community.service.storage.model.StoredObject;
import com.ying.tech.community.service.storage.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章附件服务实现类。
 * <p>
 * 该类负责处理文章附件的完整生命周期管理，包括：
 * <ul>
 *   <li>附件上传：文件校验、对象存储交互、元数据持久化</li>
 *   <li>附件绑定：将已上传的附件关联到具体文章</li>
 *   <li>附件替换：更新文章的附件列表，自动释放旧附件</li>
 *   <li>附件释放：解除附件与文章的绑定关系</li>
 *   <li>附件查询：统计和检索已绑定的附件信息</li>
 * </ul>
 * </p>
 * <p>
 * 核心设计原则：
 * <ul>
 *   <li>先上传到对象存储，成功后再写入数据库，避免孤儿记录</li>
 *   <li>数据库操作失败时反向删除 OSS 文件，保证数据一致性</li>
 *   <li>附件状态流转：UPLOADED(已上传未绑定) -> BOUND(已绑定到文章)</li>
 *   <li>严格的用户权限校验，防止跨用户访问附件资源</li>
 * </ul>
 * </p>
 */
@Service
public class ArticleAttachmentServiceImpl implements ArticleAttachmentService {
    /**
     * 日期格式化器，用于生成对象键中的年月目录路径。
     * 格式：yyyyMM（例如：202604 表示 2026 年 4 月）
     * 作用：按时间维度归档附件，便于管理和清理
     */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * MyBatis-Plus Mapper 接口，用于操作 article_attachment 表
     */
    private final ArticleAttachmentMapper articleAttachmentMapper;
    
    /**
     * 对象存储服务接口，封装了与 OSS/MinIO/S3 等存储服务的交互
     */
    private final ObjectStorageService objectStorageService;
    
    /**
     * OSS 配置属性，包含文件大小限制、允许的扩展名和 MIME 类型等
     */
    private final OssProperties ossProperties;

    /**
     * 构造函数注入依赖（Spring 推荐的最佳实践）
     *
     * @param articleAttachmentMapper 附件数据访问层
     * @param objectStorageService    对象存储服务
     * @param ossProperties           OSS 配置属性
     */
    public ArticleAttachmentServiceImpl(ArticleAttachmentMapper articleAttachmentMapper,
                                        ObjectStorageService objectStorageService,
                                        OssProperties ossProperties) {
        this.articleAttachmentMapper = articleAttachmentMapper;
        this.objectStorageService = objectStorageService;
        this.ossProperties = ossProperties;
    }

    /**
     * 上传文章附件。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>基础校验：检查文件是否为空、大小是否超限</li>
     *   <li>安全处理：标准化文件名、提取扩展名、规范化 Content-Type</li>
     *   <li>类型校验：同时验证 MIME 类型和文件扩展名白名单</li>
     *   <li>生成对象键：按"业务/用户ID/年月/随机串-文件名"规则构建存储路径</li>
     *   <li>上传到 OSS：将文件流传输到对象存储</li>
     *   <li>持久化元数据：将附件信息保存到数据库（状态为 UPLOADED）</li>
     *   <li>异常回滚：数据库写入失败时删除 OSS 文件，避免资源泄漏</li>
     * </ol>
     * </p>
     *
     * @param file 前端上传的文件对象
     * @return 附件视图对象，包含附件 ID、文件名、URL 等信息
     * @throws BusinessException 当文件为空、超大、类型不合法或上传失败时抛出
     */
    @Override
    public ArticleAttachmentVO uploadAttachment(MultipartFile file) {
        // ========== 第一步：基础空文件校验 ==========
        // 避免无效请求进入后续存储流程，减少不必要的资源消耗
        if (file == null || file.isEmpty()) {
            throw new BusinessException(StatusEnum.FILE_EMPTY);
        }

        long fileSize = file.getSize();
        
        // ========== 第二步：文件大小校验 ==========
        // 在上传前就拦截超大文件，减少网络带宽和存储开销
        if (fileSize > ossProperties.getMaxFileSize().toBytes()) {
            throw new BusinessException(StatusEnum.FILE_TOO_LARGE);
        }

        // ========== 第三步：文件名和安全处理 ==========
        // 1. 清理文件名中的非法字符和路径信息，防止目录穿越攻击
        // 2. 提取文件扩展名并转为小写，统一格式
        // 3. 规范化 Content-Type，去掉 charset 等附加参数
        String safeFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(safeFileName);
        String contentType = normalizeContentType(file.getContentType());
        
        // ========== 第四步：文件类型白名单校验 ==========
        // 同时验证 MIME 类型和扩展名，双重保障防止绕过安全检查
        validateFileType(contentType, extension);

        // ========== 第五步：构建对象存储键（Object Key）==========
        // 获取当前登录用户 ID，用于隔离不同用户的附件资源
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        // 生成唯一的对象键，格式：article/{userId}/{yyyyMM}/{UUID}-{filename}
        String objectKey = buildObjectKey(userId, safeFileName);
        
        // ========== 第六步：上传到对象存储 ==========
        StoredObject storedObject;
        try (var inputStream = file.getInputStream()) {
            // 调用对象存储服务上传文件，返回存储后的元数据（objectKey 和 URL）
            storedObject = objectStorageService.upload(objectKey, inputStream, fileSize, contentType);
        } catch (IOException e) {
            // IO 异常通常由网络问题或流读取失败引起，转换为业务异常
            throw new BusinessException(StatusEnum.FILE_UPLOAD_FAILED.getCode(), StatusEnum.FILE_UPLOAD_FAILED.getMsg());
        }

        // ========== 第七步：构建数据库实体对象 ==========
        // 只有 OSS 上传成功后才准备数据库记录，遵循"先存储后入库"原则
        ArticleAttachmentDO attachment = new ArticleAttachmentDO();
        attachment.setUserId(userId);                          // 附件所有者
        attachment.setOriginFileName(safeFileName);            // 原始文件名（ sanitized ）
        attachment.setObjectKey(storedObject.objectKey());     // OSS 中的唯一标识
        attachment.setUrl(storedObject.url());                 // 可公开访问的 URL
        attachment.setContentType(contentType);                // MIME 类型
        attachment.setFileSize(fileSize);                      // 文件大小（字节）
        attachment.setFileExt(extension);                      // 文件扩展名
        attachment.setStatus(ArticleAttachmentStatusConstants.UPLOADED); // 初始状态：已上传未绑定

        // ========== 第八步：持久化到数据库 ==========
        try {
            articleAttachmentMapper.insert(attachment);
        } catch (Exception e) {
            // ========== 第九步：异常回滚机制 ==========
            // 数据库落库失败时，立即删除 OSS 中已上传的文件
            // 避免出现"孤儿对象"（OSS 有文件但数据库无记录），造成存储浪费和管理困难
            objectStorageService.delete(storedObject.objectKey());
            throw e; // 重新抛出异常，让调用方感知失败
        }

        // ========== 第十步：转换为 VO 对象返回 ==========
        return toVO(attachment);
    }

    /**
     * 将已上传的附件绑定到指定文章。
     * <p>
     * 使用场景：用户发布新文章时，将之前上传的草稿附件关联到正式文章。
     * </p>
     * <p>
     * 核心校验规则：
     * <ul>
     *   <li>附件必须存在且属于当前用户（防止跨用户挪用）</li>
     *   <li>附件状态必须为 UPLOADED（已上传未绑定）</li>
     *   <li>附件不能已经绑定到其他文章（articleId 必须为 null）</li>
     *   <li>保留传入的顺序，支持前端自定义排序（如封面选择）</li>
     * </ul>
     * </p>
     *
     * @param articleId     目标文章 ID
     * @param userId        当前用户 ID（用于权限校验）
     * @param attachmentIds 待绑定的附件 ID 列表（可重复，会自动去重）
     * @return 按传入顺序排列的已绑定附件列表
     * @throws BusinessException 当附件不存在、无权访问或已被绑定时抛出
     */
    @Override
    public List<ArticleAttachmentDO> bindAttachmentsToArticle(Long articleId, Long userId, List<Long> attachmentIds) {
        // 边界条件：如果附件列表为空，直接返回空列表（不是异常场景）
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return Collections.emptyList();
        }
    
        // ========== 第一步：去重并保持顺序 ==========
        // 使用 LinkedHashSet 去除重复 ID，同时保留首次出现的顺序
        // 原因：前端可能因重试等原因发送重复 ID，需要容错处理
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(attachmentIds));
            
        // ========== 第二步：批量查询附件信息 ==========
        // 一次性查出所有附件，避免 N+1 查询问题
        List<ArticleAttachmentDO> attachments = articleAttachmentMapper.selectBatchIds(uniqueIds);
            
        // 校验：查询结果数量必须等于请求的 ID 数量，否则说明有 ID 不存在
        if (attachments.size() != uniqueIds.size()) {
            throw new BusinessException(StatusEnum.ATTACHMENT_NOT_FOUND);
        }
    
        // ========== 第三步：构建 ID -> 附件对象的映射 ==========
        // 使用 LinkedHashMap 保持插入顺序，便于后续按原始顺序重组
        Map<Long, ArticleAttachmentDO> attachmentMap = attachments.stream()
                .collect(Collectors.toMap(ArticleAttachmentDO::getId, attachment -> attachment, (left, right) -> left, LinkedHashMap::new));
    
        // ========== 第四步：逐个校验并绑定附件 ==========
        List<ArticleAttachmentDO> orderedAttachments = new ArrayList<>(uniqueIds.size());
        for (Long attachmentId : uniqueIds) {
            ArticleAttachmentDO attachment = attachmentMap.get(attachmentId);
            if (attachment == null) {
                // 理论上不会走到这里（前面已校验数量），但作为防御性编程保留
                throw new BusinessException(StatusEnum.ATTACHMENT_NOT_FOUND);
            }
                
            // 校验 1：权限检查 - 只能绑定自己上传的附件
            // 防止恶意用户通过枚举 ID 挪用他人的附件资源
            if (!userId.equals(attachment.getUserId())) {
                throw new BusinessException(StatusEnum.ATTACHMENT_ACCESS_DENIED);
            }
                
            // 校验 2：状态检查 - 必须是"已上传未绑定"状态且 articleId 为 null
            // 防止重复绑定到多篇文章，保证附件与文章的一对多关系（一篇文章可有多个附件，一个附件只能属于一篇文章）
            if (!Integer.valueOf(ArticleAttachmentStatusConstants.UPLOADED).equals(attachment.getStatus())
                    || attachment.getArticleId() != null) {
                throw new BusinessException(StatusEnum.ATTACHMENT_ALREADY_BOUND);
            }
                
            // ========== 第五步：更新附件绑定关系 ==========
            attachment.setArticleId(articleId);                                    // 关联到目标文章
            attachment.setStatus(ArticleAttachmentStatusConstants.BOUND);          // 状态变更为"已绑定"
            articleAttachmentMapper.updateById(attachment);                        // 持久化到数据库
            orderedAttachments.add(attachment);                                    // 加入结果列表（保持顺序）
        }
        return orderedAttachments;
    }

    /**
     * 替换文章的附件列表（用于文章编辑场景）。
     * <p>
     * 与 {@link #bindAttachmentsToArticle} 的区别：
     * <ul>
     *   <li>该方法会先查询文章当前已绑定的附件</li>
     *   <li>自动释放不在新列表中的旧附件（回退到 UPLOADED 状态）</li>
     *   <li>复用已绑定到当前文章的附件，避免不必要的数据库更新</li>
     *   <li>支持清空所有附件（传入空列表即可）</li>
     * </ul>
     * </p>
     * <p>
     * 典型使用场景：用户编辑文章时修改了附件列表（增删改）
     * </p>
     *
     * @param articleId     目标文章 ID
     * @param userId        当前用户 ID（用于权限校验）
     * @param attachmentIds 新的附件 ID 列表（如果为空则清空所有附件）
     * @return 替换后的附件列表（按传入顺序排列）
     * @throws BusinessException 当新附件列表中的附件不存在、无权访问或已被其他文章绑定时抛出
     */
    @Override
    public List<ArticleAttachmentDO> replaceAttachmentsOnArticle(Long articleId, Long userId, List<Long> attachmentIds) {
        // ========== 第一步：查询当前已绑定的附件 ==========
        QueryWrapper<ArticleAttachmentDO> currentQuery = new QueryWrapper<>();
        currentQuery.eq("article_id", articleId)
                .eq("status", ArticleAttachmentStatusConstants.BOUND)
                .orderByAsc("id"); // 按 ID 升序，保证顺序稳定
        List<ArticleAttachmentDO> currentAttachments = articleAttachmentMapper.selectList(currentQuery);
            
        // ========== 第二步：处理清空附件的场景 ==========
        if (CollectionUtils.isEmpty(attachmentIds)) {
            // 如果新列表为空，释放所有当前绑定的附件
            releaseAttachments(currentAttachments);
            return Collections.emptyList();
        }
    
        // ========== 第三步：去重并查询新附件列表 ==========
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(attachmentIds));
        List<ArticleAttachmentDO> attachments = articleAttachmentMapper.selectBatchIds(uniqueIds);
        if (attachments.size() != uniqueIds.size()) {
            throw new BusinessException(StatusEnum.ATTACHMENT_NOT_FOUND);
        }
    
        // 构建 ID -> 附件对象的映射
        Map<Long, ArticleAttachmentDO> attachmentMap = attachments.stream()
                .collect(Collectors.toMap(ArticleAttachmentDO::getId, attachment -> attachment, (left, right) -> left, LinkedHashMap::new));
            
        // 将新附件 ID 集合化，便于快速判断是否存在
        LinkedHashSet<Long> targetIds = new LinkedHashSet<>(uniqueIds);
            
        // ========== 第四步：释放不再需要的旧附件 ==========
        for (ArticleAttachmentDO currentAttachment : currentAttachments) {
            // 如果旧附件不在新列表中，需要解除绑定（回退到 UPLOADED 状态）
            // 这样用户可以重新使用该附件，而不是直接删除
            if (!targetIds.contains(currentAttachment.getId())) {
                resetAttachmentBinding(currentAttachment.getId());
            }
        }
    
        // ========== 第五步：绑定新附件列表 ==========
        List<ArticleAttachmentDO> orderedAttachments = new ArrayList<>(uniqueIds.size());
        for (Long attachmentId : uniqueIds) {
            ArticleAttachmentDO attachment = attachmentMap.get(attachmentId);
            if (attachment == null) {
                throw new BusinessException(StatusEnum.ATTACHMENT_NOT_FOUND);
            }
                
            // 校验 1：权限检查 - 只能绑定自己的附件
            if (!userId.equals(attachment.getUserId())) {
                throw new BusinessException(StatusEnum.ATTACHMENT_ACCESS_DENIED);
            }
                
            // 优化：如果附件已经绑定到当前文章，直接复用，避免重复更新数据库
            if (Objects.equals(articleId, attachment.getArticleId())
                    && Integer.valueOf(ArticleAttachmentStatusConstants.BOUND).equals(attachment.getStatus())) {
                orderedAttachments.add(attachment);
                continue;
            }
                
            // 校验 2：状态检查 - 必须是"已上传未绑定"状态
            if (!Integer.valueOf(ArticleAttachmentStatusConstants.UPLOADED).equals(attachment.getStatus())
                    || attachment.getArticleId() != null) {
                throw new BusinessException(StatusEnum.ATTACHMENT_ALREADY_BOUND);
            }
                
            // 执行绑定操作
            attachment.setArticleId(articleId);
            attachment.setStatus(ArticleAttachmentStatusConstants.BOUND);
            articleAttachmentMapper.updateById(attachment);
            orderedAttachments.add(attachment);
        }
        return orderedAttachments;
    }

    /**
     * 释放文章的所有附件（通常在文章删除时调用）。
     * <p>
     * 注意：该方法只解除绑定关系，不删除 OSS 中的实际文件。
     * 释放后的附件状态回退为 UPLOADED，可以被重新绑定到其他文章。
     * </p>
     *
     * @param articleId 目标文章 ID
     */
    @Override
    public void releaseAttachmentsOnArticle(Long articleId) {
        // 查询该文章下所有已绑定的附件
        QueryWrapper<ArticleAttachmentDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("article_id", articleId)
                .eq("status", ArticleAttachmentStatusConstants.BOUND);
        List<ArticleAttachmentDO> attachments = articleAttachmentMapper.selectList(queryWrapper);
        
        // 批量释放附件
        releaseAttachments(attachments);
    }

    /**
     * 批量统计多篇文章的附件数量。
     * <p>
     * 使用数据库聚合查询（GROUP BY），避免将大量附件明细加载到内存中统计。
     * 适用于文章列表页展示每篇文章的附件数。
     * </p>
     *
     * @param articleIds 文章 ID 列表
     * @return Map<文章ID, 附件数量>，没有附件的文章不会出现在结果中
     */
    @Override
    public Map<Long, Long> countBoundAttachments(List<Long> articleIds) {
        // 边界条件：空列表直接返回空 Map
        if (CollectionUtils.isEmpty(articleIds)) {
            return Collections.emptyMap();
        }

        // ========== 数据库聚合查询 ==========
        // SELECT article_id AS articleId, COUNT(*) AS attachmentCount
        // FROM article_attachment
        // WHERE article_id IN (...) AND status = BOUND
        // GROUP BY article_id
        QueryWrapper<ArticleAttachmentDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("article_id AS articleId", "COUNT(*) AS attachmentCount")
                .in("article_id", articleIds)
                .eq("status", ArticleAttachmentStatusConstants.BOUND)
                .groupBy("article_id");

        // ========== 解析查询结果 ==========
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : articleAttachmentMapper.selectMaps(queryWrapper)) {
            Number articleId = (Number) row.get("articleId");
            Number attachmentCount = (Number) row.get("attachmentCount");
            if (articleId != null && attachmentCount != null) {
                result.put(articleId.longValue(), attachmentCount.longValue());
            }
        }
        return result;
    }

    /**
     * 查询文章的所有已绑定附件（用于文章详情页展示）。
     * <p>
     * 返回结果按 ID 升序排列，保证顺序稳定。
     * 只返回状态为 BOUND 的附件，排除已释放的附件。
     * </p>
     *
     * @param articleId 文章 ID
     * @return 附件 VO 列表
     */
    @Override
    public List<ArticleAttachmentVO> listBoundAttachments(Long articleId) {
        QueryWrapper<ArticleAttachmentDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("article_id", articleId)
                .eq("status", ArticleAttachmentStatusConstants.BOUND)
                .orderByAsc("id"); // 按 ID 升序，保证顺序一致
        
        // 将 DO 对象转换为 VO 对象（隐藏内部字段，只暴露前端需要的信息）
        return articleAttachmentMapper.selectList(queryWrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 校验文件类型是否符合白名单要求。
     * <p>
     * 采用双重校验策略：
     * <ul>
     *   <li>MIME 类型校验：基于文件内容的真实类型（由浏览器或后端检测）</li>
     *   <li>扩展名校验：基于文件名的后缀</li>
     * </ul>
     * 两者必须同时通过，防止通过修改文件扩展名或伪造 Content-Type 绕过安全检查。
     * </p>
     *
     * @param contentType HTTP 请求中的 Content-Type 头（已规范化）
     * @param extension   文件扩展名（已转小写）
     * @throws BusinessException 当类型不在白名单中时抛出
     */
    private void validateFileType(String contentType, String extension) {
        List<String> allowedContentTypes = ossProperties.getAllowedContentTypes();
        // MIME 类型白名单校验
        if (!CollectionUtils.isEmpty(allowedContentTypes) && !allowedContentTypes.contains(contentType)) {
            throw new BusinessException(StatusEnum.FILE_TYPE_NOT_ALLOWED);
        }

        List<String> allowedExtensions = ossProperties.getAllowedExtensions();
        // 文件扩展名白名单校验
        if (!CollectionUtils.isEmpty(allowedExtensions) && !allowedExtensions.contains(extension)) {
            throw new BusinessException(StatusEnum.FILE_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * 构建对象存储的唯一键（Object Key）。
     * <p>
     * 键的格式：article/{userId}/{yyyyMM}/{UUID}-{filename}
     * </p>
     * <p>
     * 设计考虑：
     * <ul>
     *   <li>按用户 ID 分目录：便于权限隔离和审计</li>
     *   <li>按年月分目录：便于定期清理和统计分析</li>
     *   <li>UUID 前缀：保证文件名唯一性，避免覆盖冲突</li>
     *   <li>保留原始文件名：便于人工识别和管理</li>
     * </ul>
     * </p>
     *
     * @param userId       用户 ID
     * @param safeFileName 已清洗的文件名
     * @return 完整的对象键路径
     */
    private String buildObjectKey(Long userId, String safeFileName) {
        // 获取当前年月（例如：202604）
        String month = LocalDate.now().format(MONTH_FORMATTER);
        // 生成不带横杠的 UUID（32 位十六进制字符串）
        String token = UUID.randomUUID().toString().replace("-", "");
        // 拼接完整路径：article/123/202604/abc123def456-example.png
        return "article/" + userId + "/" + month + "/" + token + "-" + safeFileName;
    }

    /**
     * 清洗文件名，移除潜在的安全风险。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>如果文件名为空，使用默认名称 "file"</li>
     *   <li>将反斜杠 \ 替换为正斜杠 /（统一路径分隔符）</li>
     *   <li>提取最后一个 / 之后的部分（防止目录穿越攻击，如 ../../etc/passwd）</li>
     *   <li>将所有非字母数字、非 ._\- 的字符替换为下划线 _</li>
     *   <li>如果处理后为空，返回默认名称 "file"</li>
     * </ol>
     * </p>
     *
     * @param originalFileName 原始文件名（可能包含恶意字符）
     * @return 安全的文件名
     */
    private String sanitizeFileName(String originalFileName) {
        // 处理空文件名情况
        String candidate = StringUtils.hasText(originalFileName) ? originalFileName : "file";
        
        // 统一路径分隔符为正斜杠
        candidate = candidate.replace('\\', '/');
        
        // 提取文件名本体，移除路径信息（防止目录穿越）
        int slashIndex = candidate.lastIndexOf('/');
        if (slashIndex >= 0) {
            candidate = candidate.substring(slashIndex + 1);
        }
        
        // 只保留安全字符：字母、数字、点、下划线、连字符
        // 其他字符（包括中文、空格、特殊符号）全部替换为下划线
        candidate = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        
        // 兜底：如果清洗后文件名为空（例如原文件名全是特殊字符），使用默认名
        if (!StringUtils.hasText(candidate)) {
            return "file";
        }
        return candidate;
    }

    /**
     * 从文件名中提取扩展名。
     * <p>
     * 使用 Spring 的工具方法提取最后一个 . 之后的部分，并转为小写。
     * 例如：Example.PNG -> png，archive.tar.gz -> gz
     * </p>
     *
     * @param fileName 文件名
     * @return 小写的扩展名（不含点），如果没有扩展名则返回空字符串
     */
    private String extractExtension(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        return StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 规范化 Content-Type，移除附加参数。
     * <p>
     * 处理示例：
     * <ul>
     *   <li>"image/jpeg; charset=UTF-8" -> "image/jpeg"</li>
     *   <li>"text/plain" -> "text/plain"</li>
     *   <li>null 或空字符串 -> "application/octet-stream"</li>
     * </ul>
     * </p>
     * <p>
     * 为什么要规范化？
     * 浏览器上传时可能在 Content-Type 后附加 charset 等参数，
     * 但白名单配置中通常只包含标准 MIME 类型（如 image/jpeg），
     * 因此需要去除附加参数才能正确匹配。
     * </p>
     *
     * @param contentType 原始 Content-Type 字符串
     * @return 规范化后的 MIME 类型（小写）
     */
    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        // 去掉形如 charset=UTF-8 的附加参数，统一以标准 MIME 主值做比较
        String normalized = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : "application/octet-stream";
    }

    /**
     * 将数据对象（DO）转换为视图对象（VO）。
     * <p>
     * DO 包含数据库中的所有字段（包括内部使用的 userId、objectKey 等），
     * 而 VO 只暴露前端需要的字段，起到数据过滤和安全隔离的作用。
     * </p>
     *
     * @param attachment 数据库实体对象
     * @return 前端视图对象
     */
    private ArticleAttachmentVO toVO(ArticleAttachmentDO attachment) {
        ArticleAttachmentVO vo = new ArticleAttachmentVO();
        vo.setAttachmentId(attachment.getId());              // 附件 ID
        vo.setFileName(attachment.getOriginFileName());      // 原始文件名
        vo.setUrl(attachment.getUrl());                      // 可访问的 URL
        vo.setContentType(attachment.getContentType());      // MIME 类型
        vo.setFileSize(attachment.getFileSize());            // 文件大小
        return vo;
    }

    /**
     * 批量释放附件列表。
     * <p>
     * 遍历附件列表，逐个调用 {@link #resetAttachmentBinding} 解除绑定关系。
     * </p>
     *
     * @param attachments 待释放的附件列表
     */
    private void releaseAttachments(List<ArticleAttachmentDO> attachments) {
        if (CollectionUtils.isEmpty(attachments)) {
            return;
        }
        for (ArticleAttachmentDO attachment : attachments) {
            resetAttachmentBinding(attachment.getId());
        }
    }

    /**
     * 重置单个附件的绑定关系。
     * <p>
     * 该方法是释放附件的核心逻辑，执行以下操作：
     * <ul>
     *   <li>将 article_id 字段设置为 NULL（解除与文章的关联）</li>
     *   <li>将 status 字段重置为 UPLOADED（标记为"已上传未绑定"状态）</li>
     * </ul>
     * </p>
     * <p>
     * 重要：该方法不删除 OSS 中的实际文件，只是解除数据库层面的绑定关系。
     * 这样做的好处是用户可以重新使用该附件，避免重复上传。
     * </p>
     *
     * @param attachmentId 附件 ID
     */
    private void resetAttachmentBinding(Long attachmentId) {
        // 使用 UpdateWrapper 进行部分字段更新，只修改 article_id 和 status
        // 不删除文件本身，让附件可以被重新绑定到其他文章
        articleAttachmentMapper.update(null, new UpdateWrapper<ArticleAttachmentDO>()
                .eq("id", attachmentId)
                .set("article_id", null)
                .set("status", ArticleAttachmentStatusConstants.UPLOADED));
    }
}
