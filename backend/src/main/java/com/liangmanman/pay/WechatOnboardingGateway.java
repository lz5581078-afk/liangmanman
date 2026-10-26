package com.liangmanman.pay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.liangmanman.common.BizException;
import com.liangmanman.entity.MerchantAccount;
import com.wechat.pay.java.core.cipher.PrivacyEncryptor;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.Constant;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.HttpMethod;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.core.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 收付通二级商户进件（小微商户）。
 * 接口：POST /v3/ecommerce/applyments/、GET /v3/ecommerce/applyments/out-request-no/{out_request_no}
 * 身份证照先调图片上传接口换 media_id；姓名、证件号、卡号、手机号用微信支付公钥（或平台证书）加密。
 * 字段依据微信支付“电商收付通-二级商户进件”文档，首次联调请对照最新文档逐项核对。
 */
@Slf4j
@Component
@Conditional(PaymentConditions.Wechat.class)
public class WechatOnboardingGateway implements OnboardingGateway {

    private final WechatPayClients wx;

    public WechatOnboardingGateway(WechatPayClients wx) {
        this.wx = wx;
    }

    @Override
    public String submit(Application a) {
        PrivacyEncryptor enc = wx.getEncryptor();
        String frontId = upload("id_front.jpg", a.idCardFront());
        String backId = upload("id_back.jpg", a.idCardBack());

        JsonObject idCard = new JsonObject();
        idCard.addProperty("id_card_copy", frontId);
        idCard.addProperty("id_card_national", backId);
        idCard.addProperty("id_card_name", enc.encrypt(a.idName()));
        idCard.addProperty("id_card_number", enc.encrypt(a.idNumber()));
        idCard.addProperty("id_card_valid_time_begin", a.idValidFrom());
        idCard.addProperty("id_card_valid_time", a.idValidTo());

        JsonObject account = new JsonObject();
        account.addProperty("bank_account_type", "75"); // 对私账户
        account.addProperty("account_bank", a.bankName());
        account.addProperty("account_name", enc.encrypt(a.idName()));
        account.addProperty("bank_address_code", a.bankCityCode());
        account.addProperty("account_number", enc.encrypt(a.bankAccount()));

        JsonObject contact = new JsonObject();
        contact.addProperty("contact_type", "65"); // 经营者本人
        contact.addProperty("contact_name", enc.encrypt(a.idName()));
        contact.addProperty("mobile_phone", enc.encrypt(a.mobile()));

        JsonObject scene = new JsonObject();
        scene.addProperty("store_name", a.storeName());
        scene.addProperty("mini_program_sub_appid", wx.getSpAppId());

        JsonObject body = new JsonObject();
        body.addProperty("out_request_no", a.outRequestNo());
        body.addProperty("organization_type", "2401"); // 小微商户
        body.addProperty("id_doc_type", "IDENTIFICATION_TYPE_MAINLAND_IDCARD");
        body.add("id_card_info", idCard);
        body.addProperty("need_account_info", true);
        body.add("account_info", account);
        body.add("contact_info", contact);
        body.add("sales_scene_info", scene);
        body.addProperty("merchant_shortname", a.shortName());

        try {
            JsonObject r = call(HttpMethod.POST, "/v3/ecommerce/applyments/", body.toString(), enc.getWechatpaySerial());
            return r.get("applyment_id").getAsString();
        } catch (ServiceException e) {
            log.warn("进件提交失败 {}：{}", a.outRequestNo(), e.getErrorMessage());
            throw new BizException("开户资料提交失败：" + e.getErrorMessage());
        }
    }

    @Override
    public Result query(String outRequestNo) {
        JsonObject r = call(HttpMethod.GET, "/v3/ecommerce/applyments/out-request-no/" + outRequestNo, null, null);
        String state = r.has("applyment_state") ? r.get("applyment_state").getAsString() : "";
        String subMchId = r.has("sub_mchid") ? r.get("sub_mchid").getAsString() : null;
        String signUrl = r.has("sign_url") ? r.get("sign_url").getAsString() : null;
        String mapped = switch (state) {
            case "FINISH" -> MerchantAccount.APPROVED;
            case "NEED_SIGN" -> MerchantAccount.NEED_SIGN;
            case "REJECTED", "CANCELED", "FROZEN" -> MerchantAccount.REJECTED;
            default -> MerchantAccount.AUDITING; // CHECKING / ACCOUNT_NEED_VERIFY / AUDITING
        };
        String reason = null;
        if (MerchantAccount.REJECTED.equals(mapped) && r.has("audit_detail")) {
            StringBuilder sb = new StringBuilder();
            JsonArray details = r.getAsJsonArray("audit_detail");
            for (JsonElement d : details) {
                JsonObject o = d.getAsJsonObject();
                if (o.has("reject_reason")) {
                    if (!sb.isEmpty()) sb.append("；");
                    sb.append(o.get("reject_reason").getAsString());
                }
            }
            reason = sb.isEmpty() ? state : sb.toString();
        }
        return new Result(mapped, subMchId, signUrl, reason);
    }

    private String upload(String fileName, byte[] image) {
        try {
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
            String meta = "{\"filename\":\"" + fileName + "\",\"sha256\":\"" + sha256 + "\"}";
            return wx.getFileUpload().uploadImage(WechatPayClients.API + "/v3/merchant/media/upload", meta, fileName, image).getMediaId();
        } catch (ServiceException e) {
            throw new BizException("证件照上传失败：" + e.getErrorMessage());
        } catch (Exception e) {
            throw new BizException("证件照上传失败，请重试");
        }
    }

    private JsonObject call(HttpMethod method, String path, String jsonBody, String serial) {
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader(Constant.ACCEPT, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.CONTENT_TYPE, MediaType.APPLICATION_JSON.getValue());
        if (serial != null) {
            headers.addHeader(Constant.WECHAT_PAY_SERIAL, serial);
        }
        HttpRequest.Builder b = new HttpRequest.Builder().httpMethod(method).url(WechatPayClients.API + path).headers(headers);
        if (jsonBody != null) {
            b.body(new JsonRequestBody.Builder().body(jsonBody).build());
        }
        return wx.getHttpClient().execute(b.build(), JsonObject.class).getServiceResponse();
    }
}
