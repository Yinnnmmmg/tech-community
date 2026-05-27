package com.ying.tech.community.service.sms.service;

import com.ying.tech.community.core.exception.BusinessException;
import com.ying.tech.community.core.exception.StatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SmsVerificationService {

    @Autowired
    private SmsService smsService;

    public void verifyCode(String phone, String code) {
        String cachedCode = smsService.getCachedCode(phone);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(StatusEnum.SMS_CODE_ERROR);
        }
    }

    public void consumeCode(String phone) {
        smsService.consumeCode(phone);
    }
}
