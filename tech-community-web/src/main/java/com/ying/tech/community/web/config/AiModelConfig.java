package com.ying.tech.community.web.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiModelConfig {

    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(DeepSeekChatModel deepSeekChatModel){
        return ChatClient.builder(deepSeekChatModel).build();
    }
}
