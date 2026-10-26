package com.liangmanman.controller;

import com.liangmanman.pay.PaymentConditions;
import com.liangmanman.pay.WechatEcommercePaymentService;
import com.liangmanman.service.OrderService;
import com.wechat.pay.java.core.notification.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信支付回调（支付结果、退款结果）。验签和解密由 SDK 完成。
 * 按微信要求：处理成功返回 200 空响应，失败返回 4xx/5xx + {code, message}，微信会重试。
 */
@Slf4j
@RestController
@Conditional(PaymentConditions.Wechat.class)
public class PayNotifyController {

    private final WechatEcommercePaymentService wechat;
    private final OrderService orderService;

    public PayNotifyController(WechatEcommercePaymentService wechat, OrderService orderService) {
        this.wechat = wechat;
        this.orderService = orderService;
    }

    @PostMapping("/api/pay/notify/wechat")
    public ResponseEntity<?> payNotify(HttpServletRequest request, @RequestBody String body) {
        try {
            String[] r = wechat.parsePayNotify(param(request, body));
            if (r != null) {
                orderService.onPaid(r[0], r[1]);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "处理失败"));
        }
    }

    @PostMapping("/api/pay/notify/refund")
    public ResponseEntity<?> refundNotify(HttpServletRequest request, @RequestBody String body) {
        try {
            wechat.handleRefundNotify(param(request, body));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("处理退款回调失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "处理失败"));
        }
    }

    private static RequestParam param(HttpServletRequest req, String body) {
        return new RequestParam.Builder()
                .serialNumber(req.getHeader("Wechatpay-Serial"))
                .nonce(req.getHeader("Wechatpay-Nonce"))
                .signature(req.getHeader("Wechatpay-Signature"))
                .timestamp(req.getHeader("Wechatpay-Timestamp"))
                .signType(req.getHeader("Wechatpay-Signature-Type"))
                .body(body)
                .build();
    }
}
