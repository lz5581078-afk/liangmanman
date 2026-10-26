package com.liangmanman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 腾讯云 API 3.0 通用调用（TC3-HMAC-SHA256 签名）。短信、语音通知、文字识别共用。
 * 签名规则：https://cloud.tencent.com/document/api/213/30654
 */
@Slf4j
@Component
public class TencentCloudClient {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final AppProperties.Tencent cfg;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.create();

    public TencentCloudClient(AppProperties props, ObjectMapper objectMapper) {
        this.cfg = props.tencent();
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return cfg.configured();
    }

    /** 调用接口，返回 Response 节点；接口报错时抛 TencentCloudException */
    public JsonNode call(String service, String version, String action, Object payload) {
        if (!configured()) {
            throw new TencentCloudException("NotConfigured", "未配置腾讯云密钥");
        }
        String host = service + ".tencentcloudapi.com";
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        long timestamp = Instant.now().getEpochSecond();
        String authorization = authorization(cfg.secretId(), cfg.secretKey(), service, host, timestamp, body);

        String text = http.post()
                .uri("https://" + host)
                .header("Authorization", authorization)
                .header("Host", host)
                .header("X-TC-Action", action)
                .header("X-TC-Version", version)
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .header("X-TC-Region", cfg.region())
                .contentType(MediaType.parseMediaType(CONTENT_TYPE))
                .body(body.getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .body(String.class);
        try {
            JsonNode resp = objectMapper.readTree(text).path("Response");
            if (resp.has("Error")) {
                throw new TencentCloudException(resp.path("Error").path("Code").asText(),
                        resp.path("Error").path("Message").asText());
            }
            return resp;
        } catch (TencentCloudException e) {
            throw e;
        } catch (Exception e) {
            throw new TencentCloudException("BadResponse", "腾讯云返回无法解析");
        }
    }

    /** 生成 Authorization 头。单独拆出来便于测试。 */
    static String authorization(String secretId, String secretKey, String service, String host,
                                long timestamp, String payload) {
        String date = DATE.format(Instant.ofEpochSecond(timestamp));
        String canonicalRequest = "POST\n/\n\n"
                + "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n\n"
                + "content-type;host\n"
                + sha256Hex(payload);
        String scope = date + "/" + service + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, service);
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        return ALGORITHM + " Credential=" + secretId + "/" + scope
                + ", SignedHeaders=content-type;host, Signature=" + signature;
    }

    static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static class TencentCloudException extends RuntimeException {
        private final String code;

        public TencentCloudException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
