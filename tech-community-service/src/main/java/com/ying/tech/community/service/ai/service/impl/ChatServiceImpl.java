package com.ying.tech.community.service.ai.service.impl;

import cn.hutool.core.lang.UUID;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.ai.entity.ChatMessageItem;
import com.ying.tech.community.service.ai.entity.ChatRole;
import com.ying.tech.community.service.ai.service.ChatService;
import com.ying.tech.community.service.ai.vo.ChatStreamVO;
import com.ying.tech.community.service.ai.vo.ReferenceVO;
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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
    public Flux<ChatStreamVO> streamChat(String sessionId, String question) {
        /// 开始用延迟创建流，只有被订阅时才执行块中的代码
        return Flux.defer(() -> {
            ///1、获取之前的人机交互的聊天记录（按时间顺序）
            List<ChatMessageItem> chatHistoryList = new ArrayList<ChatMessageItem>(redisTemplate.opsForZSet()
                                                    .range(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, 0, -1));
            List<Message> HistoryMessageList = (chatHistoryList == null ? Set.<ChatMessageItem>of() : chatHistoryList).stream()
                    .map(history -> history.getRole().equals(ChatRole.USER)
                            ? new UserMessage(history.getContent())
                            : new AssistantMessage(history.getContent()))
                    .collect(Collectors.toList());
            ///调用大模型且利用角色限定和上传聊天记录来改造用户问题
            String userQuestion = chatClient.prompt().system(GET_MATERIAL_SYSTEM_PROMPT).user(question).messages(HistoryMessageList).call().content();
            ///2、 从向量数据库中获取参考资料用于填充referenceVO和组装userPrompt
            List<Document> documentList = vectorStore.similaritySearch(userQuestion);
            // 填充referenceVO集合
            HashSet<ReferenceVO> referenceVOS = new HashSet<ReferenceVO>();
            // documentList是切割后的，所以要利用Set去重
            // 遍历documentList，将每一个document转成ReferenceVO，并添加到referenceVOS中
            documentList.forEach(document -> {
                Long articleId = extractArticleId(document);
                String title = extractTitle(document);
                if (articleId != null && title != null && !title.isBlank()) {
                    referenceVOS.add(ReferenceVO.builder()
                            .articleId(articleId)
                            .title(title)
                            .build());
                }
            });
            ///组装 userPrompt
            StringBuilder stringBuilder = new StringBuilder();
            // 只拼参考资料的正文内容
            documentList.forEach(document -> {
                String content = document.getText();
                if (content != null && !content.isBlank()) {
                    stringBuilder.append(content).append("\n");
                }
            });
            String material = stringBuilder.toString();
            String userPrompt = "基于以下参考资料：" + material + "回答以下问题：" + userQuestion;
            ///保存用户聊天记录到redis  TTL设为24小时（实现短期记忆）
            String userMessageId = UUID.randomUUID().toString();
            String assistantMessageId = UUID.randomUUID().toString();
            saveUserMessage(sessionId, question, userMessageId);
            ///组装prompt，发送请求
            Flux<String> aiAskStream = chatClient.prompt()
                    .system(GET_RESULT_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .messages(HistoryMessageList)
                    .stream()
                    .content();
            ///边返回给前端边保存ai的聊天记录到redis  TTL设为24小时（实现短期记忆）
            StringBuilder askContentBuilder = new StringBuilder();
            return aiAskStream
                    .doOnNext(askContentBuilder::append)
                    //将字符串类型的返回值转成ChatStreamVO
                    .map(chunkText -> ChatStreamVO.builder()
                            .content(chunkText)
                            .isEnd(false)
                            .build())
                    //拼接一个流
                    .concatWith(Mono.fromSupplier(() -> {
                        //保存ai的聊天记录
                        saveAssistantMessage(sessionId, assistantMessageId, askContentBuilder.toString());
                        //返回正常结束的流的最后一块数据
                        return buildNormalEndStream(referenceVOS);
                    }));
        }).onErrorResume(throwable -> {
            //发生错误时降级处理，返回错误结束的流
            log.error("AI SSE stream failed, sessionId={}", sessionId, throwable);
            return Flux.just(buildErrorEndStream(throwable));
        });
    }

    private void saveUserMessage(String sessionId, String question, String userMessageId) {
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
    }

    private void saveAssistantMessage(String sessionId, String assistantMessageId, String content) {
        ChatMessageItem assistantMessageItem = ChatMessageItem.builder()
                .messageId(assistantMessageId)
                .sessionId(sessionId)
                .role(ChatRole.ASSISTANT)
                .content(content)
                .createdTime(LocalDateTime.now())
                .build();
        /// 使用 ZSet，以时间戳作为 score 保证顺序（比用户消息晚1毫秒）
        double assistantScore = System.currentTimeMillis() + 1;
        redisTemplate.opsForZSet().add(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, assistantMessageItem, assistantScore);
        /// 为整个 key 设置 24 小时过期时间
        redisTemplate.expire(TECH_COMMUNITY_AI_CHAT_SESSION_MESSAGES + sessionId, 24, TimeUnit.HOURS);
    }

    private ChatStreamVO buildNormalEndStream(Set<ReferenceVO> referenceVOS) {
        return ChatStreamVO.builder()
                .content("")
                .isEnd(true)
                .references(referenceVOS)
                .build();
    }

    private ChatStreamVO buildErrorEndStream(Throwable throwable) {
        Integer errorCode = StatusEnum.UNEXPECTED_ERROR.getCode();
        String errorMessage = StatusEnum.UNEXPECTED_ERROR.getMsg();
        if (throwable instanceof BusinessException businessException) {
            errorCode = businessException.getCode();
            errorMessage = businessException.getMsg();
        }
        return ChatStreamVO.builder()
                .content("")
                .isEnd(true)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private Long extractArticleId(Document document) {
        Object articleIdValue = document.getMetadata().get("articleId");
        if (articleIdValue instanceof Number number) {
            return number.longValue();
        }
        if (articleIdValue instanceof String articleIdText && !articleIdText.isBlank()) {
            try {
                return Long.parseLong(articleIdText);
            } catch (NumberFormatException e) {
                log.warn("RAG metadata articleId格式错误, articleId={}", articleIdText);
            }
        }
        return null;
    }

    private String extractTitle(Document document) {
        Object titleValue = document.getMetadata().get("title");
        return titleValue instanceof String title ? title : null;
    }
}
