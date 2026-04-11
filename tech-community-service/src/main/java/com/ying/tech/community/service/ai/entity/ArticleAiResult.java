package com.ying.tech.community.service.ai.entity;

public record ArticleAiResult(
        Boolean isSafe,      // 核心风控指标
        String reason,       // 若isSafe为false，给出简短原因；若为true，可为空白
        String summary      // 80字以内的技术摘要

){}
