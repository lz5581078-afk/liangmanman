package com.liangmanman.pay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liangmanman.common.NoGenerator;
import com.liangmanman.entity.Order;
import com.liangmanman.entity.Payment;
import com.liangmanman.entity.Refund;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Withdraw;
import com.liangmanman.mapper.OrderMapper;
import com.liangmanman.mapper.PaymentMapper;
import com.liangmanman.mapper.RefundMapper;
import com.liangmanman.mapper.SettlementMapper;
import com.liangmanman.mapper.WithdrawMapper;
import com.liangmanman.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模拟支付：生成支付单，前端拿到 mock=true 后调用 /api/pay/mock/{outTradeNo}/success 模拟“支付成功”回调。
 * 余额按本地结算记录计算，提现立即成功。
 */
@Slf4j
@Service
@Conditional(PaymentConditions.Mock.class)
public class MockPaymentService implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final SettlementMapper settlementMapper;
    private final WithdrawMapper withdrawMapper;
    private final OrderMapper orderMapper;

    public MockPaymentService(PaymentMapper paymentMapper, RefundMapper refundMapper, SettlementMapper settlementMapper,
                              WithdrawMapper withdrawMapper, OrderMapper orderMapper) {
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.settlementMapper = settlementMapper;
        this.withdrawMapper = withdrawMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public boolean available(Store store) {
        return true;
    }

    @Override
    public PayParams prepay(Order order, Store store, String openId) {
        // 同一订单重复点支付，复用未完成的支付单
        Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, order.getId())
                .eq(Payment::getStatus, Payment.PENDING)
                .last("LIMIT 1"));
        if (pay == null) {
            pay = new Payment();
            pay.setOrderId(order.getId());
            pay.setOutTradeNo(NoGenerator.next("P"));
            pay.setChannel("MOCK");
            pay.setSubMchId("MOCK_" + store.getId());
            pay.setAmount(order.getTotalAmount());
            pay.setStatus(Payment.PENDING);
            paymentMapper.insert(pay);
        }
        return new PayParams(true, pay.getOutTradeNo(), null, null, null, null, null);
    }

    @Override
    public boolean refund(Order order, String reason) {
        Refund r = new Refund();
        r.setOrderId(order.getId());
        r.setOutRefundNo(NoGenerator.next("R"));
        r.setAmount(order.getTotalAmount());
        r.setStatus("SUCCESS");
        r.setReason(reason);
        refundMapper.insert(r);
        log.info("[模拟退款] 订单 {} 退款 {} 元", order.getOrderNo(), order.getTotalAmount());
        return true;
    }

    @Override
    public String release(Settlement settlement) {
        log.info("[模拟解冻] 订单 {} 解冻 {} 元给店铺 {}", settlement.getOrderId(), settlement.getAmount(), settlement.getStoreId());
        return null;
    }

    @Override
    public Balance balance(Store store) {
        BigDecimal released = sum(settlementMapper.selectMaps(new QueryWrapper<Settlement>()
                .select("COALESCE(SUM(amount), 0) AS s").eq("store_id", store.getId()).eq("status", Settlement.RELEASED)));
        BigDecimal withdrawn = sum(withdrawMapper.selectMaps(new QueryWrapper<Withdraw>()
                .select("COALESCE(SUM(amount), 0) AS s").eq("store_id", store.getId()).ne("status", Withdraw.FAILED)));
        BigDecimal frozenSettle = sum(settlementMapper.selectMaps(new QueryWrapper<Settlement>()
                .select("COALESCE(SUM(amount), 0) AS s").eq("store_id", store.getId()).ne("status", Settlement.RELEASED)));
        BigDecimal frozenOrders = sum(orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("COALESCE(SUM(total_amount), 0) AS s").eq("store_id", store.getId()).eq("paid", true)
                .notIn("status", OrderStatus.COMPLETED.name(), OrderStatus.CANCELLED.name())));
        return new Balance(released.subtract(withdrawn), frozenSettle.add(frozenOrders));
    }

    @Override
    public WithdrawResult withdraw(Withdraw withdraw) {
        return new WithdrawResult(Withdraw.SUCCESS, "MOCK" + withdraw.getOutNo(), null);
    }

    @Override
    public WithdrawResult queryWithdraw(Withdraw withdraw) {
        return new WithdrawResult(Withdraw.SUCCESS, withdraw.getWithdrawId(), null);
    }

    private static BigDecimal sum(List<Map<String, Object>> rows) {
        if (rows.isEmpty() || rows.get(0) == null) return BigDecimal.ZERO;
        Object v = rows.get(0).values().stream().findFirst().orElse(null);
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
