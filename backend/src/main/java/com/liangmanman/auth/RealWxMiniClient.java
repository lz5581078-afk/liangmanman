package com.liangmanman.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.common.BizException;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 正式环境：调用微信 jscode2session 接口 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.wx.mock", havingValue = "false", matchIfMissing = true)
public class RealWxMiniClient implements WxMiniClient {

    private final RestClient http = RestClient.create("https://api.weixin.qq.com");
    private final ObjectMapper objectMapper;
    private final AppProperties.Wx wx;

    public RealWxMiniClient(ObjectMapper objectMapper, AppProperties props) {
        this.objectMapper = objectMapper;
        this.wx = props.wx();
        if (wx.appId() == null || wx.appId().isBlank()) {
            throw new IllegalStateException("未配置 WX_APP_ID / WX_APP_SECRET；本地开发请使用 dev 配置（app.wx.mock=true）");
        }
    }

    @Override
    public WxSession code2Session(String code) {
        // 微信这个接口返回的 Content-Type 是 text/plain，所以先取字符串再解析
        String body = http.get()
                .uri(b -> b.path("/sns/jscode2session")
                        .queryParam("appid", wx.appId())
                        .queryParam("secret", wx.appSecret())
                        .queryParam("js_code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .retrieve()
                .body(String.class);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
                log.warn("微信登录失败: {}", body);
                throw new BizException(BizException.UNAUTHORIZED, "微信登录失败，请重试");
            }
            String unionId = node.hasNonNull("unionid") ? node.get("unionid").asText() : null;
            return new WxSession(node.get("openid").asText(), unionId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信登录结果失败: {}", body, e);
            throw new BizException(BizException.UNAUTHORIZED, "微信登录失败，请重试");
        }
    }
}
