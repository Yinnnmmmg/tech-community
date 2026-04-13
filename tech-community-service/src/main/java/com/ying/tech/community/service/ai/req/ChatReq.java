package com.ying.tech.community.service.ai.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatReq {
    /**
     * 会话ID，唯一标识一个对话窗口。
     * 建议由前端生成（如 UUID），后端基于此ID去 Redis 存取该窗口的历史记忆。
     */
    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    /**
     * 用户输入的问题
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题过长，请精简至1000字以内")
    private String question;
}
