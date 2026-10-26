package com.liangmanman.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.auth.UserContext;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Payment;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.PaymentMapper;
import com.liangmanman.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅开发环境存在：模拟“微信支付成功”回调 */
@RestController
@ConditionalOnProperty(name = "app.pay.mock", havingValue = "true")
public class MockPayController {

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    public MockPayController(OrderService orderService, PaymentMapper paymentMapper, OrderMapper orderMapper) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
    }

    @PostMapping("/api/pay/mock/{outTradeNo}/success")
    public ApiResult<Void> success(@PathVariable String outTradeNo) {
        Long uid = UserContext.require();
        Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOutTradeNo, outTradeNo));
        if (pay == null) {
            throw BizException.notFound("支付单");
        }
        Order o = orderMapper.selectById(pay.getOrderId());
        if (o == null || !o.getBuyerId().equals(uid)) {
            throw BizException.notFound("支付单");
        }
        orderService.onPaid(outTradeNo, "MOCK" + outTradeNo);
        return ApiResult.ok();
    }
}
