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
        if (!smsService.verifyCode(phone, code)) {
            throw new BusinessException(StatusEnum.SMS_CODE_ERROR);
        }
    }
}
