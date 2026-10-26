package com.liangmanman.pay;

import com.liangmanman.common.BizException;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Withdraw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 正式环境还没配置收付通：在线支付关闭，平台只跑货到付款 */
@Slf4j
@Service
@Conditional(PaymentConditions.Disabled.class)
public class DisabledPaymentService implements PaymentService {

    @Override
    public boolean available(Store store) {
        return false;
    }

    @Override
    public PayParams prepay(Order order, Store store, String openId) {
        throw new BizException("在线支付暂未开通，请选择货到付款");
    }

    @Override
    public boolean refund(Order order, String reason) {
        log.error("在线支付未开通却需要退款，订单 {}，请人工处理", order.getOrderNo());
        return false;
    }

    @Override
    public String release(Settlement settlement) {
        return "在线支付未开通";
    }

    @Override
    public Balance balance(Store store) {
        return new Balance(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public WithdrawResult withdraw(Withdraw withdraw) {
        throw new BizException("在线收款暂未开通");
    }

    @Override
    public WithdrawResult queryWithdraw(Withdraw withdraw) {
        return new WithdrawResult(Withdraw.FAILED, null, "在线收款未开通");
    }
}
