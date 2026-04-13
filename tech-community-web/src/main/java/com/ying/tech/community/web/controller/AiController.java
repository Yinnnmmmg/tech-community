package com.ying.tech.community.web.controller;

import com.ying.tech.community.service.ai.req.ChatReq;
import com.ying.tech.community.service.ai.service.ChatService;
import com.ying.tech.community.service.ai.vo.ChatStreamVO;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ChatService ChatService;

    @PostMapping(value = "/chat", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamVO>> chat(@RequestBody @Validated ChatReq req) {

        // 调用 Service 层拿到大模型的增量 Flux 文本流
        Flux<String> aiStream = ChatService.streamChat(req.getSessionId(), req.getQuestion());

        // 将普通的文本流映射为带有规范结构的 SSE 事件流
        return aiStream
                .map(chunkText -> ServerSentEvent.<ChatStreamVO>builder()
                        .data(ChatStreamVO.builder()
                                .content(chunkText)
                                .isEnd(false)
                                .build())
                        .build())
                // 在数据流正常生成结束时，追加最后一条带引用的终结标识块
                .concatWith(Flux.just(ServerSentEvent.<ChatStreamVO>builder()
                        .data(ChatStreamVO.builder()
                                .content("")
                                .isEnd(true)
                                // 此处从 RAG 上下文中提取出本次命中的文章列表进行封装
                                /*.references(articleList)*/
                                .build())
                        .build()));
    }

}
