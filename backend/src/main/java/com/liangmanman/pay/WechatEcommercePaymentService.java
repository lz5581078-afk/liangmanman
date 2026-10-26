package com.liangmanman.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.liangmanman.common.BizException;
import com.liangmanman.common.NoGenerator;
import com.liangmanman.entity.MerchantAccount;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Payment;
import com.liangmanman.entity.Refund;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Withdraw;
import com.liangmanman.mapper.MerchantAccountMapper;
import com.liangmanman.mapper.PaymentMapper;
import com.liangmanman.mapper.RefundMapper;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.Constant;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.HttpMethod;
import com.wechat.pay.java.core.http.HttpRequest;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.core.http.MediaType;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.ecommerceprofitsharing.model.FinishOrderRequest;
import com.wechat.pay.java.service.ecommercerefund.model.CreateRefundRequest;
import com.wechat.pay.java.service.ecommercerefund.model.Refund4Create;
import com.wechat.pay.java.service.ecommercerefund.model.RefundReqAmount;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.Amount;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.Payer;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.SettleInfo;
import com.wechat.pay.java.service.partnerpayments.jsapi.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 微信支付“电商收付通”。
 *
 * <ol>
 *   <li>下单：服务商 JSAPI 支付，sub_mchid = 店铺的二级商户号，settle_info.profit_sharing = true（资金冻结）</li>
 *   <li>订单完成：调用“完结分账”，平台不分钱，全部解冻到店铺余额</li>
 *   <li>店铺在小程序里点提现：调用二级商户提现接口，钱到店铺自己的银行卡</li>
 * </ol>
 * 接口路径与字段以微信支付官方文档为准，首次联调请逐项核对。
 */
@Slf4j
@Service
@Conditional(PaymentConditions.Wechat.class)
public class WechatEcommercePaymentService implements PaymentService {

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final WechatPayClients wx;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final MerchantAccountMapper accountMapper;

    public WechatEcommercePaymentService(WechatPayClients wx, PaymentMapper paymentMapper,
                                         RefundMapper refundMapper, MerchantAccountMapper accountMapper) {
        this.wx = wx;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.accountMapper = accountMapper;
    }

    @Override
    public boolean available(Store store) {
        return subMchId(store.getId()) != null;
    }

    private String subMchId(Long storeId) {
        MerchantAccount a = accountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>()
                .eq(MerchantAccount::getStoreId, storeId));
        return a != null && MerchantAccount.APPROVED.equals(a.getStatus()) ? a.getSubMchId() : null;
    }

    // ---------- 支付 ----------

    @Override
    public PayParams prepay(Order order, Store store, String openId) {
        String subMchId = subMchId(store.getId());
        if (subMchId == null) {
            throw new BizException("这家店还没开通在线收款，请选择货到付款");
        }
        Payment pay = new Payment();
        pay.setOrderId(order.getId());
        pay.setOutTradeNo(NoGenerator.next("P"));
        pay.setChannel("WECHAT");
        pay.setSubMchId(subMchId);
        pay.setAmount(order.getTotalAmount());
        pay.setStatus(Payment.PENDING);
        paymentMapper.insert(pay);

        PrepayRequest req = new PrepayRequest();
        req.setSpAppid(wx.getSpAppId());
        req.setSpMchid(wx.getCfg().spMchId());
        req.setSubMchid(subMchId);
        req.setDescription(cut("粮满满-" + store.getName(), 120));
        req.setOutTradeNo(pay.getOutTradeNo());
        req.setNotifyUrl(wx.notifyUrl("/api/pay/notify/wechat"));
        req.setTimeExpire(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusMinutes(15).format(RFC3339));
        Amount amount = new Amount();
        amount.setTotal(fen(order.getTotalAmount()).intValue());
        amount.setCurrency("CNY");
        req.setAmount(amount);
        Payer payer = new Payer();
        payer.setSpOpenid(openId);
        req.setPayer(payer);
        SettleInfo settle = new SettleInfo();
        settle.setProfitSharing(true); // 冻结，订单完成后再解冻给店铺
        req.setSettleInfo(settle);
        try {
            PrepayWithRequestPaymentResponse r = wx.getJsapi().prepayWithRequestPayment(req, wx.getSpAppId());
            return new PayParams(false, pay.getOutTradeNo(), r.getTimeStamp(), r.getNonceStr(),
                    r.getPackageVal(), r.getSignType(), r.getPaySign());
        } catch (ServiceException e) {
            log.error("微信下单失败 {}：{}", order.getOrderNo(), e.getErrorMessage());
            throw new BizException("微信支付下单失败：" + e.getErrorMessage());
        }
    }

    /** 支付结果通知：验签解密，返回 [outTradeNo, transactionId]；未成功返回 null */
    public String[] parsePayNotify(RequestParam param) {
        Transaction t = wx.getNotificationParser().parse(param, Transaction.class);
        if (t.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return null;
        }
        return new String[]{t.getOutTradeNo(), t.getTransactionId()};
    }

    // ---------- 退款 ----------

    @Override
    public boolean refund(Order order, String reason) {
        Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, Payment.SUCCESS)
                .last("LIMIT 1"));
        if (pay == null) {
            log.error("订单 {} 没有成功的支付记录，无法退款", order.getOrderNo());
            return false;
        }
        Refund r = new Refund();
        r.setOrderId(order.getId());
        r.setOutRefundNo(NoGenerator.next("R"));
        r.setAmount(pay.getAmount());
        r.setStatus("PROCESSING");
        r.setReason(reason);
        refundMapper.insert(r);

        CreateRefundRequest req = new CreateRefundRequest();
        req.setSubMchid(pay.getSubMchId());
        req.setSpAppid(wx.getSpAppId());
        req.setOutTradeNo(pay.getOutTradeNo());
        req.setOutRefundNo(r.getOutRefundNo());
        req.setReason(cut(reason, 80));
        req.setNotifyUrl(wx.notifyUrl("/api/pay/notify/refund"));
        RefundReqAmount amount = new RefundReqAmount();
        amount.setRefund(fen(pay.getAmount()));
        amount.setTotal(fen(pay.getAmount()));
        amount.setCurrency("CNY");
        req.setAmount(amount);
        try {
            Refund4Create resp = wx.getRefunds().createRefund(req);
            refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                    .eq(Refund::getId, r.getId()).set(Refund::getRefundId, resp.getRefundId()));
            return true;
        } catch (ServiceException e) {
            log.error("退款失败 {}：{}", order.getOrderNo(), e.getErrorMessage());
            refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                    .eq(Refund::getId, r.getId()).set(Refund::getStatus, "FAILED"));
            return false;
        }
    }

    /** 退款结果通知 */
    public void handleRefundNotify(RequestParam param) {
        RefundNotify n = wx.getNotificationParser().parse(param, RefundNotify.class);
        String status = "SUCCESS".equals(n.refundStatus) ? "SUCCESS"
                : ("PROCESSING".equals(n.refundStatus) ? "PROCESSING" : "FAILED");
        refundMapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getOutRefundNo, n.outRefundNo).set(Refund::getStatus, status));
        if (!"SUCCESS".equals(status)) {
            log.error("退款 {} 结果：{}，需要人工处理", n.outRefundNo, n.refundStatus);
        }
    }

    static class RefundNotify {
        @SerializedName("out_refund_no")
        String outRefundNo;
        @SerializedName("refund_status")
        String refundStatus;
    }

    // ---------- 解冻（完结分账） ----------

    @Override
    public String release(Settlement s) {
        FinishOrderRequest req = new FinishOrderRequest();
        req.setSubMchid(s.getSubMchId());
        req.setTransactionId(s.getTransactionId());
        req.setOutOrderNo("F" + s.getOrderId());
        req.setDescription("订单完成，货款解冻");
        try {
            wx.getProfitSharing().finishOrder(req);
            return null;
        } catch (ServiceException e) {
            return e.getErrorCode() + " " + e.getErrorMessage();
        }
    }

    // ---------- 余额与提现（二级商户） ----------

    @Override
    public Balance balance(Store store) {
        String subMchId = subMchId(store.getId());
        if (subMchId == null) {
            return new Balance(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        JsonObject r = call(HttpMethod.GET, "/v3/ecommerce/fund/balance/" + subMchId, null);
        return new Balance(yuan(r, "available_amount"), yuan(r, "pending_amount"));
    }

    @Override
    public WithdrawResult withdraw(Withdraw w) {
        JsonObject body = new JsonObject();
        body.addProperty("sub_mchid", w.getSubMchId());
        body.addProperty("out_request_no", w.getOutNo());
        body.addProperty("amount", fen(w.getAmount()));
        body.addProperty("remark", "粮满满提现");
        try {
            JsonObject r = call(HttpMethod.POST, "/v3/ecommerce/fund/withdraw", body.toString());
            return new WithdrawResult(Withdraw.PROCESSING, r.has("withdraw_id") ? r.get("withdraw_id").getAsString() : null, null);
        } catch (ServiceException e) {
            return new WithdrawResult(Withdraw.FAILED, null, e.getErrorMessage());
        }
    }

    @Override
    public WithdrawResult queryWithdraw(Withdraw w) {
        JsonObject r = call(HttpMethod.GET, "/v3/ecommerce/fund/withdraw/out-request-no/" + w.getOutNo()
                + "?sub_mchid=" + w.getSubMchId(), null);
        String status = r.has("status") ? r.get("status").getAsString() : "";
        String reason = r.has("reason") ? r.get("reason").getAsString() : null;
        return switch (status) {
            case "SUCCESS" -> new WithdrawResult(Withdraw.SUCCESS, w.getWithdrawId(), null);
            case "FAIL", "REFUND", "CLOSE" -> new WithdrawResult(Withdraw.FAILED, w.getWithdrawId(), reason == null ? status : reason);
            default -> new WithdrawResult(Withdraw.PROCESSING, w.getWithdrawId(), null);
        };
    }

    // ---------- 工具 ----------

    JsonObject call(HttpMethod method, String path, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader(Constant.ACCEPT, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.CONTENT_TYPE, MediaType.APPLICATION_JSON.getValue());
        HttpRequest.Builder b = new HttpRequest.Builder()
                .httpMethod(method)
                .url(WechatPayClients.API + path)
                .headers(headers);
        if (jsonBody != null) {
            b.body(new JsonRequestBody.Builder().body(jsonBody).build());
        }
        return wx.getHttpClient().execute(b.build(), JsonObject.class).getServiceResponse();
    }

    static Long fen(BigDecimal yuan) {
        return yuan.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal yuan(JsonObject o, String key) {
        return o.has(key) ? BigDecimal.valueOf(o.get(key).getAsLong()).movePointLeft(2) : BigDecimal.ZERO;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
