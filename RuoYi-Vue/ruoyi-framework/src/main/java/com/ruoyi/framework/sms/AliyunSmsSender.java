package com.ruoyi.framework.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 阿里云短信验证码发送器。
 * AccessKey 只能通过环境变量或生产配置中心注入，禁止写入源码。
 */
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender
{
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);
    private static final String PRODUCT = "Dysmsapi";
    private static final String DOMAIN = "dysmsapi.aliyuncs.com";

    @Value("${sms.aliyun.region-id:cn-hangzhou}")
    private String regionId;

    @Value("${sms.aliyun.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.aliyun.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.aliyun.template-code:SMS_112145005}")
    private String templateCode;

    @Value("${sms.aliyun.out-id:}")
    private String outId;

    @Value("${sms.aliyun.signs.gas:油品位}")
    private String gasSign;

    @Value("${sms.aliyun.signs.sds:三点水}")
    private String sdsSign;

    @Value("${sms.aliyun.signs.ldy:衣贵洁}")
    private String ldySign;

    @Value("${sms.aliyun.signs.default:微创众联}")
    private String defaultSign;

    @Override
    public void sendLoginCode(String phonenumber, String code)
    {
        sendLoginCode(phonenumber, code, null);
    }

    @Override
    public void sendLoginCode(String phonenumber, String code, String platcode)
    {
        validateConfiguration();
        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        try
        {
            DefaultProfile.addEndpoint(regionId, regionId, PRODUCT, DOMAIN);
            IAcsClient client = new DefaultAcsClient(profile);
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumbers(phonenumber);
            request.setSignName(resolveSignName(platcode));
            request.setTemplateCode(templateCode);
            request.setTemplateParam("{\"no\":\"" + code + "\"}");
            if (StringUtils.isNotEmpty(outId))
            {
                request.setOutId(outId);
            }
            SendSmsResponse response = client.getAcsResponse(request);
            if (response == null || !"OK".equals(response.getCode()))
            {
                String responseCode = response == null ? "EMPTY_RESPONSE" : response.getCode();
                String requestId = response == null ? "" : response.getRequestId();
                log.error("Aliyun SMS failed: code={}, requestId={}", responseCode, requestId);
                throw new ServiceException("验证码发送失败，请稍后再试");
            }
            log.info("Aliyun SMS sent to {}, requestId={}", maskPhone(phonenumber), response.getRequestId());
        }
        catch (Exception ex)
        {
            log.error("Aliyun SMS request failed: {}", ex.getClass().getSimpleName());
            throw new ServiceException("验证码发送失败，请稍后再试");
        }
    }

    private void validateConfiguration()
    {
        if (StringUtils.isEmpty(accessKeyId) || StringUtils.isEmpty(accessKeySecret)
                || StringUtils.isEmpty(templateCode) || StringUtils.isEmpty(defaultSign))
        {
            throw new ServiceException("阿里云短信配置不完整");
        }
    }

    private String resolveSignName(String code)
    {
        if ("gas".equalsIgnoreCase(code))
        {
            return gasSign;
        }
        if ("sds".equalsIgnoreCase(code))
        {
            return sdsSign;
        }
        if ("ldy".equalsIgnoreCase(code))
        {
            return ldySign;
        }
        return defaultSign;
    }

    private String maskPhone(String phone)
    {
        return phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
