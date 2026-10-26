package com.liangmanman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.liangmanman.common.BizException;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序服务端接口：接口调用凭证、小程序码、订阅消息、文本内容安全。
 * wx.mock=true 时：小程序码用普通二维码代替，订阅消息只记日志，内容安全只拦截测试词。
 */
@Slf4j
@Service
public class WxApi {

    /** 开发 / 测试用：包含这个词的文字视为违规 */
    public static final String MOCK_BANNED_WORD = "违禁测试";

    private final AppProperties.Wx cfg;
    private final ObjectMapper objectMapper;
    private final RestClient http = RestClient.create("https://api.weixin.qq.com");

    private volatile String token;
    private volatile long tokenExpireAt;

    public WxApi(AppProperties props, ObjectMapper objectMapper) {
        this.cfg = props.wx();
        this.objectMapper = objectMapper;
    }

    // ---------- 接口调用凭证（stable_token，自动缓存到过期前 5 分钟） ----------

    private synchronized String accessToken() {
        if (token != null && Instant.now().getEpochSecond() < tokenExpireAt) {
            return token;
        }
        JsonNode r = postJson("/cgi-bin/stable_token", Map.of(
                "grant_type", "client_credential", "appid", cfg.appId(), "secret", cfg.appSecret()), false);
        if (!r.hasNonNull("access_token")) {
            throw new IllegalStateException("获取 access_token 失败：" + r);
        }
        token = r.get("access_token").asText();
        tokenExpireAt = Instant.now().getEpochSecond() + r.path("expires_in").asLong(7200) - 300;
        return token;
    }

    // ---------- 小程序码 ----------

    /**
     * 不限数量的小程序码（getwxacodeunlimit）。scene 最长 32 个字符，如 s=12。
     * page 为小程序页面路径，不带开头的 /。
     */
    public byte[] qrCode(String scene, String page) {
        if (cfg.mock()) {
            return plainQr("liangmanman://" + page + "?scene=" + scene);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        body.put("page", page);
        body.put("check_path", false);
        body.put("width", 430);
        ResponseEntity<byte[]> resp = http.post()
                .uri(b -> b.path("/wxa/getwxacodeunlimit").queryParam("access_token", accessToken()).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(byte[].class);
        MediaType type = resp.getHeaders().getContentType();
        if (type != null && type.includes(MediaType.APPLICATION_JSON)) {
            log.warn("生成小程序码失败：{}", new String(resp.getBody()));
            throw new BizException("生成小程序码失败，请稍后再试");
        }
        return resp.getBody();
    }

    private byte[] plainQr(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 430, 430, Map.of(EncodeHintType.MARGIN, 1));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < m.getWidth(); x++) {
                for (int y = 0; y < m.getHeight(); y++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x1F1D19 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 订阅消息 ----------

    /** 发送订阅消息，返回 null 成功，否则是失败原因（用户没授权时微信返回 43101） */
    public String sendSubscribe(String openId, String templateId, String page, Map<String, String> data) {
        if (cfg.mock()) {
            log.info("[模拟订阅消息] {} 模板 {} 页面 {} 内容 {}", openId, templateId, page, data);
            return null;
        }
        if (templateId == null || templateId.isBlank()) {
            return "未配置订阅消息模板";
        }
        if (openId == null) {
            return "没有 openid";
        }
        Map<String, Object> wrapped = new LinkedHashMap<>();
        data.forEach((k, v) -> wrapped.put(k, Map.of("value", v)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openId);
        body.put("template_id", templateId);
        body.put("page", page);
        body.put("data", wrapped);
        try {
            JsonNode r = postJson("/cgi-bin/message/subscribe/send", body, true);
            int code = r.path("errcode").asInt(0);
            return code == 0 ? null : code + " " + r.path("errmsg").asText();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    // ---------- 文本内容安全（msg_sec_check v2） ----------

    /**
     * 检查用户发布的文字（商品名、说明、店铺公告、评价、求购）。
     * scene：1 资料 2 评论 3 论坛 4 社交日志。微信接口异常时放行，避免影响商家使用，由运营后台人工抽查兜底。
     */
    public boolean textSafe(String text, String openId, int scene) {
        if (text == null || text.isBlank()) {
            return true;
        }
        if (cfg.mock()) {
            return !text.contains(MOCK_BANNED_WORD);
        }
        if (openId == null) {
            return true;
        }
        try {
            JsonNode r = postJson("/wxa/msg_sec_check", Map.of(
                    "content", text, "version", 2, "scene", scene, "openid", openId), true);
            if (r.path("errcode").asInt(0) != 0) {
                log.warn("内容安全接口返回错误：{}", r);
                return true;
            }
            return !"risky".equals(r.path("result").path("suggest").asText());
        } catch (Exception e) {
            log.warn("内容安全检查失败，放行：{}", e.getMessage());
            return true;
        }
    }

    private JsonNode postJson(String path, Object body, boolean withToken) {
        String text = http.post()
                .uri(b -> {
                    b.path(path);
                    if (withToken) b.queryParam("access_token", accessToken());
                    return b.build();
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("微信接口返回无法解析：" + text);
        }
    }
}
