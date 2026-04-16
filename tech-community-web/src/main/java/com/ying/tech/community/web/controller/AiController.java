package com.ying.tech.community.web.controller;

import com.ying.tech.community.service.ai.req.ChatReq;
import com.ying.tech.community.service.ai.service.ChatService;
import com.ying.tech.community.service.ai.vo.ChatStreamVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private ChatService chatService;

    /**
     * 基于 RAG 的社区私有知识库问答
     * */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<ChatStreamVO>> chat(@RequestBody @Validated ChatReq req) {

        // 调用 Service 层拿到已经封装好的聊天流
        Flux<ChatStreamVO> aiStream = chatService.streamChat(req.getSessionId(), req.getQuestion());

        // 仅负责将聊天流包装为 SSE 事件流
        return aiStream
                .map(chatStreamVO -> ServerSentEvent.<ChatStreamVO>builder()
                        .data(chatStreamVO)
                        .build());
    }

}
