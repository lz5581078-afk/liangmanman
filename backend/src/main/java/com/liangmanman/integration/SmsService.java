package com.liangmanman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 腾讯云短信（SendSms 2021-01-11）。通知 mock 时只记日志。 */
@Slf4j
@Service
public class SmsService {

    private final TencentCloudClient tencent;
    private final AppProperties.Notify cfg;

    public SmsService(TencentCloudClient tencent, AppProperties props) {
        this.tencent = tencent;
        this.cfg = props.notification();
    }

    /** 返回 null 表示成功，否则是失败原因 */
    public String send(String phone, String templateId, List<String> params) {
        if (cfg.mock()) {
            log.info("[模拟短信] {} 模板 {} 参数 {}", phone, templateId, params);
            return null;
        }
        if (templateId == null || templateId.isBlank() || cfg.smsSdkAppId() == null || cfg.smsSdkAppId().isBlank()) {
            return "未配置短信模板";
        }
        if (!isMobile(phone)) {
            return "不是手机号，无法发短信";
        }
        try {
            JsonNode resp = tencent.call("sms", "2021-01-11", "SendSms", Map.of(
                    "PhoneNumberSet", List.of("+86" + phone),
                    "SmsSdkAppId", cfg.smsSdkAppId(),
                    "SignName", cfg.smsSign(),
                    "TemplateId", templateId,
                    "TemplateParamSet", params));
            JsonNode status = resp.path("SendStatusSet").path(0);
            return "Ok".equals(status.path("Code").asText()) ? null : status.path("Message").asText("发送失败");
        } catch (Exception e) {
            log.warn("短信发送失败 {}：{}", phone, e.getMessage());
            return e.getMessage();
        }
    }

    static boolean isMobile(String phone) {
        return phone != null && phone.matches("^1\\d{10}$");
    }
}
