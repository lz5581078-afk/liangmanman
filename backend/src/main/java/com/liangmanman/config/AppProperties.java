package com.liangmanman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全部业务配置（application.yml 的 app.*）。没配置的分组取默认值，方便本地开发只写必要的几项。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        String uploadDir,
        String privateDir,
        String jwtSecret,
        int jwtDays,
        Store store,
        Order order,
        Search search,
        Wx wx,
        Pay pay,
        Tencent tencent,
        Notify notification,
        Admin admin) {

    public AppProperties {
        if (privateDir == null || privateDir.isBlank()) privateDir = "./private";
        if (store == null) store = new Store(false);
        if (order == null) order = new Order(15, 30, 24, 10, 10);
        if (search == null) search = new Search(30);
        if (wx == null) wx = new Wx(true, null, null, null, null);
        if (pay == null) pay = new Pay(true, null);
        if (tencent == null) tencent = new Tencent(null, null, "ap-guangzhou", true);
        if (notification == null) notification = new Notify(true, null, null, null, null, null, null, false);
        if (admin == null) admin = new Admin(null, null);
    }

    public record Store(boolean autoApprove) {}

    public record Order(int payTimeoutMinutes,
                        int acceptTimeoutMinutes,
                        int autoConfirmHours,
                        int expressAutoConfirmDays,
                        /** 下单多少分钟没接单，再打一次电话催老板 */
                        int remindAfterMinutes) {
        public Order {
            if (remindAfterMinutes <= 0) remindAfterMinutes = 10;
        }
    }

    public record Search(double defaultRadiusKm) {}

    /**
     * 微信小程序。
     * subscribeOrderTemplate：买家“订单状态通知”订阅消息模板 ID；
     * subscribeMerchantTemplate：商家“新订单提醒”订阅消息模板 ID。
     * 模板字段名在 WxNotifyTemplates 里统一配置。
     */
    public record Wx(boolean mock, String appId, String appSecret,
                     String subscribeOrderTemplate, String subscribeMerchantTemplate) {}

    /** mock=true 用模拟支付；否则配置了 wechat.spMchId 就走收付通，没配置则在线支付不可用（只能货到付款） */
    public record Pay(boolean mock, Wechat wechat) {
        public boolean wechatConfigured() {
            return !mock && wechat != null && wechat.spMchId() != null && !wechat.spMchId().isBlank();
        }
    }

    /**
     * 微信支付收付通（平台作为电商平台服务商）。
     * publicKeyId / publicKeyPath：微信支付公钥模式（新商户默认）；不填则用平台证书自动下载模式。
     */
    public record Wechat(String spMchId, String apiV3Key, String privateKeyPath, String merchantSerialNumber,
                         String publicKeyId, String publicKeyPath, String notifyBaseUrl) {}

    /** 腾讯云 API 密钥（短信、语音、OCR 共用）。ocrMock=true 时拍照识别返回示例结果。 */
    public record Tencent(String secretId, String secretKey, String region, boolean ocrMock) {
        public boolean configured() {
            return secretId != null && !secretId.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    /**
     * 通知。mock=true 只写日志和通知记录，不真发。
     * smsTemplateNewOrder 参数：{1}=订单号后6位 {2}=金额；smsTemplateBuyer 参数：{1}=店名 {2}=状态说明；
     * voiceTemplateNewOrder 参数：{1}=金额。
     */
    public record Notify(boolean mock, String smsSdkAppId, String smsSign,
                         String smsTemplateNewOrder, String smsTemplateBuyer,
                         String voiceSdkAppId, String voiceTemplateNewOrder,
                         boolean buyerSmsFallback) {}

    /** 首次启动、还没有任何管理员时，用它自动创建一个超级管理员 */
    public record Admin(String initUsername, String initPassword) {}
}
