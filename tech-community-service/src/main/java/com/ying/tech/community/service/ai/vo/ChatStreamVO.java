package com.ying.tech.community.service.ai.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatStreamVO {

    /**
     * 当前数据块的增量文本内容
     * 前端收到后直接拼接追加到页面已有文本的末尾
     */
    private String content;

    /**
     * 标识当前是否为最后一条数据块
     * true 表示 AI 流输出结束，前端可主动断开 SSE 连接并将全段文本格式化渲染（如 Markdown）
     */
    private Boolean isEnd;

    /**
     * RAG 引用来源列表
     * 优化点：为了节省网络带宽，该字段仅在 isEnd = true（最后一块数据）时附带具体内容，其余中间数据块中可为 null
     */
    private List<ReferenceVO> references;
}