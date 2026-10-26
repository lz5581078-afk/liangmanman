package com.liangmanman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 腾讯云语音通知（SendTtsVoice 2020-09-02）：给老板打电话念“你有新订单”。
 * 座机也能接，是农村商家最可靠的提醒方式。
 */
@Slf4j
@Service
public class VoiceService {

    private final TencentCloudClient tencent;
    private final AppProperties.Notify cfg;

    public VoiceService(TencentCloudClient tencent, AppProperties props) {
        this.tencent = tencent;
        this.cfg = props.notification();
    }

    /** 返回 null 表示成功，否则是失败原因 */
    public String call(String phone, String templateId, List<String> params) {
        if (cfg.mock()) {
            log.info("[模拟语音电话] {} 模板 {} 参数 {}", phone, templateId, params);
            return null;
        }
        if (templateId == null || templateId.isBlank() || cfg.voiceSdkAppId() == null || cfg.voiceSdkAppId().isBlank()) {
            return "未配置语音模板";
        }
        if (phone == null || !phone.replace("-", "").matches("^\\d{7,13}$")) {
            return "电话号码不对";
        }
        try {
            JsonNode resp = tencent.call("vms", "2020-09-02", "SendTtsVoice", Map.of(
                    "TemplateId", templateId,
                    "TemplateParamSet", params,
                    "CalledNumber", "+86" + phone.replace("-", ""),
                    "VoiceSdkAppid", cfg.voiceSdkAppId(),
                    "PlayTimes", 2));
            return resp.path("SendStatus").hasNonNull("CallId") ? null : "呼叫失败";
        } catch (Exception e) {
            log.warn("语音通知失败 {}：{}", phone, e.getMessage());
            return e.getMessage();
        }
    }
}
