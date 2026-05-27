package com.ying.tech.community.service.sms.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import com.ying.tech.community.service.sms.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsService {
    private static final String RATE_KEY_PREFIX = "tech-community:sms:rate:";
    private static final int RATE_TTL = 60; // 秒

    @Autowired
    private Client smsClient;
    @Autowired
    private SmsProperties smsProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void sendCode(String phone) {
        // 限流
        String rateKey = RATE_KEY_PREFIX + phone;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(rateKey, "1", RATE_TTL, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new BusinessException(StatusEnum.SMS_RATE_LIMITED);
        }

        String outId = UUID.randomUUID().toString().replace("-", "");
        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phone)
                    .setSignName(smsProperties.getSignName())
                    .setTemplateCode(smsProperties.getTemplateCode())
                    .setTemplateParam(smsProperties.getTemplateParam())
                    .setOutId(outId);

            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsVerifyCodeResponse response = smsClient.sendSmsVerifyCodeWithOptions(request, runtime);

            if (!"OK".equals(response.getBody().getCode())) {
                log.error("send sms failed, phone={}, message={}", phone, response.getBody().getMessage());
                throw new BusinessException(StatusEnum.SMS_SEND_FAILED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("send sms error, phone={}", phone, e);
            throw new BusinessException(StatusEnum.SMS_SEND_FAILED);
        }
    }

    public boolean verifyCode(String phone, String code) {
        try {
            CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(phone)
                    .setVerifyCode(code);

            RuntimeOptions runtime = new RuntimeOptions();
            CheckSmsVerifyCodeResponse response = smsClient.checkSmsVerifyCodeWithOptions(request, runtime);

            String resultCode = response.getBody().getCode();
            if ("OK".equals(resultCode)) {
                return true;
            }
            log.warn("sms code verify failed, phone={}, resultCode={}, message={}",
                    phone, resultCode, response.getBody().getMessage());
            return false;
        } catch (Exception e) {
            log.error("check sms code error, phone={}", phone, e);
            return false;
        }
    }
}
