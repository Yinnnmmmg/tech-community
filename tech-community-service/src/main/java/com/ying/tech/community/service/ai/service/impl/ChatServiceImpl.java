package com.ying.tech.community.service.ai.service.impl;

import cn.hutool.core.lang.UUID;
import com.ying.tech.community.service.ai.entity.ChatMessageItem;
import com.ying.tech.community.service.ai.entity.ChatRole;
import com.ying.tech.community.service.ai.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ying.tech.community.core.constants.AiConstants.GET_MATERIAL_SYSTEM_PROMPT;
import static com.ying.tech.community.core.constants.AiConstants.GET_RESULT_SYSTEM_PROMPT;
import static com.ying.tech.community.core.constants.RedisConstants.TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES;

/**
 * 基于 RAG 的社区私有知识库问答
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    @Override
    public Flux<String> streamChat(String sessionId, String question) {
        ///获取之前的人机交互的聊天记录（按时间顺序）
        Set<ChatMessageItem> chatHistorySet = redisTemplate.opsForZSet().range(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, 0, -1);
        List<Message> chatHistoryList = chatHistorySet.stream()
                .map(history -> history.getRole().equals(ChatRole.USER)
                        ? new UserMessage(history.getContent())
                        : new AssistantMessage(history.getContent()))
                .collect(Collectors.toList());
        ///利用角色限定和上传聊天记录来改造用户问题
        String userQuestion = chatClient.prompt().system(GET_MATERIAL_SYSTEM_PROMPT).user(question).messages(chatHistoryList).call().content();
        ///从向量数据库中获取参考资料
        List<Document> documentList = vectorStore.similaritySearch(userQuestion);
        StringBuilder stringBuilder = new StringBuilder();
        documentList.forEach(document -> stringBuilder.append(document).append("\n"));
        String material = stringBuilder.toString();
        String userPrompt = "基于以下参考资料：" + material + "回答以下问题：" + userQuestion;
        ///保存用户聊天记录到redis  TTL设为24小时（实现短期记忆）
        String userMessageId = UUID.randomUUID().toString();
        String assistantMessageId = UUID.randomUUID().toString();
        ChatMessageItem userMessageItem = ChatMessageItem.builder()
                .messageId(userMessageId)
                .sessionId(sessionId)
                .role(ChatRole.USER)
                .content(question)
                .createdTime(LocalDateTime.now())
                .build();
        /// 使用 ZSet，以时间戳作为 score 保证顺序
        double userScore = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, userMessageItem, userScore);
        /// 为整个 key 设置 24 小时过期时间
        redisTemplate.expire(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, 24, TimeUnit.HOURS);
        ///组装prompt，发送请求
        Flux<String> aiAskStream = chatClient.prompt()
                .system(GET_RESULT_SYSTEM_PROMPT)
                .user(userPrompt)
                .messages(chatHistoryList)
                .stream()
                .content();
        ///边返回给前端边保存ai的聊天记录到redis  TTL设为24小时（实现短期记忆）
        StringBuilder askContentBuilder = new StringBuilder();
        return aiAskStream.doOnNext(s -> askContentBuilder.append(s))
                .doOnComplete(() -> {
                    ChatMessageItem assistantMessageItem = ChatMessageItem.builder()
                            .messageId(assistantMessageId)
                            .sessionId(sessionId)
                            .role(ChatRole.ASSISTANT)
                            .content(askContentBuilder.toString())
                            .createdTime(LocalDateTime.now())
                            .build();
                    /// 使用 ZSet，以时间戳作为 score 保证顺序（比用户消息晚1毫秒）
                    double assistantScore = System.currentTimeMillis() + 1;
                    redisTemplate.opsForZSet().add(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, assistantMessageItem, assistantScore);
                    /// 为整个 key 设置 24 小时过期时间
                    redisTemplate.expire(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, 24, TimeUnit.HOURS);
                });
    }
}