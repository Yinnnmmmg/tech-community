package com.ying.tech.community.service.ai.service;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import reactor.core.publisher.Flux;

public interface ChatService {
    Flux<String> streamChat(@NotBlank(message = "sessionId不能为空") String sessionId, @NotBlank(message = "问题不能为空") @Size(max = 1000, message = "问题过长，请精简至1000字以内") String question);
}
