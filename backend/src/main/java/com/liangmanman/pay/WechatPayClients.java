package com.liangmanman.pay;

import com.liangmanman.config.AppProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.cipher.PrivacyEncryptor;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.ecommerceprofitsharing.EcommerceProfitSharingService;
import com.wechat.pay.java.service.ecommercerefund.EcommerceRefundService;
import com.wechat.pay.java.service.file.FileUploadService;
import com.wechat.pay.java.service.partnerpayments.jsapi.JsapiServiceExtension;
import lombok.Getter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * 微信支付 APIv3 客户端（签名、验签、敏感信息加密都由官方 SDK 处理）。
 * 两种密钥模式：
 * - 微信支付公钥模式（2024 年后新开商户默认）：配置 publicKeyId + publicKeyPath
 * - 平台证书模式：不配公钥，SDK 启动时自动下载并定期更新平台证书
 */
@Getter
@Component
@Conditional(PaymentConditions.Wechat.class)
public class WechatPayClients {

    public static final String API = "https://api.mch.weixin.qq.com";

    private final AppProperties.Wechat cfg;
    private final String spAppId;
    private final Config config;
    private final HttpClient httpClient;
    private final NotificationParser notificationParser;
    private final PrivacyEncryptor encryptor;
    private final JsapiServiceExtension jsapi;
    private final EcommerceRefundService refunds;
    private final EcommerceProfitSharingService profitSharing;
    private final FileUploadService fileUpload;

    public WechatPayClients(AppProperties props) {
        this.cfg = props.pay().wechat();
        this.spAppId = props.wx().appId();
        if (spAppId == null || spAppId.isBlank()) {
            throw new IllegalStateException("开通收付通需要同时配置 WX_APP_ID（小程序 AppID）");
        }
        if (cfg.publicKeyId() != null && !cfg.publicKeyId().isBlank()) {
            RSAPublicKeyConfig c = new RSAPublicKeyConfig.Builder()
                    .merchantId(cfg.spMchId())
                    .privateKeyFromPath(cfg.privateKeyPath())
                    .merchantSerialNumber(cfg.merchantSerialNumber())
                    .publicKeyFromPath(cfg.publicKeyPath())
                    .publicKeyId(cfg.publicKeyId())
                    .apiV3Key(cfg.apiV3Key())
                    .build();
            this.config = c;
            this.notificationParser = new NotificationParser((NotificationConfig) c);
        } else {
            RSAAutoCertificateConfig c = new RSAAutoCertificateConfig.Builder()
                    .merchantId(cfg.spMchId())
                    .privateKeyFromPath(cfg.privateKeyPath())
                    .merchantSerialNumber(cfg.merchantSerialNumber())
                    .apiV3Key(cfg.apiV3Key())
                    .build();
            this.config = c;
            this.notificationParser = new NotificationParser((NotificationConfig) c);
        }
        this.httpClient = new DefaultHttpClientBuilder().config(config).build();
        this.encryptor = config.createEncryptor();
        this.jsapi = new JsapiServiceExtension.Builder().config(config).build();
        this.refunds = new EcommerceRefundService.Builder().config(config).build();
        this.profitSharing = new EcommerceProfitSharingService.Builder().config(config).build();
        this.fileUpload = new FileUploadService.Builder().config(config).build();
    }

    public String notifyUrl(String path) {
        return cfg.notifyBaseUrl().replaceAll("/+$", "") + path;
    }
}
