package com.ying.tech.community.service.sms.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsService {
    private static final String CODE_KEY_PREFIX = "tech-community:sms:code:";
    private static final String RATE_KEY_PREFIX = "tech-community:sms:rate:";
    private static final int CODE_TTL = 5; // 分钟
    private static final int RATE_TTL = 60; // 秒
    private static final int CODE_LENGTH = 6;

    @Autowired
    private Client smsClient;
    @Autowired
    private com.ying.tech.community.service.sms.config.SmsProperties smsProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    public void sendCode(String phone) {
        String rateKey = RATE_KEY_PREFIX + phone;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(rateKey, "1", RATE_TTL, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new BusinessException(StatusEnum.SMS_RATE_LIMITED);
        }

        String code = generateCode();
        String codeKey = CODE_KEY_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL, TimeUnit.MINUTES);

        try {
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(smsProperties.getSignName())
                    .setTemplateCode(smsProperties.getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            smsClient.sendSms(sendSmsRequest);
        } catch (Exception e) {
            log.error("send sms failed, phone={}", phone, e);
            // 发送失败时删除验证码缓存，避免无效数据
            stringRedisTemplate.delete(codeKey);
            throw new BusinessException(StatusEnum.SMS_SEND_FAILED);
        }
    }

    public String getCachedCode(String phone) {
        return stringRedisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone);
    }

    public void consumeCode(String phone) {
        stringRedisTemplate.delete(CODE_KEY_PREFIX + phone);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }
}
