package com.ruoyi.framework.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development-only sender that prints the code to the backend console. */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender
{
    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public void sendLoginCode(String phonenumber, String code)
    {
        log.warn("[DEV ONLY] SMS login code for {} is {}", maskPhone(phonenumber), code);
    }

    private String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 4)
        {
            return "****";
        }
        return "******" + phone.substring(phone.length() - 4);
    }
}
