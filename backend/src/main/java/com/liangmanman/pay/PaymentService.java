package com.liangmanman.pay;

import com.liangmanman.entity.Order;
import com.liangmanman.entity.Settlement;
import com.liangmanman.entity.Store;
import com.liangmanman.entity.Withdraw;

import java.math.BigDecimal;

/**
 * 在线收款与资金。三种实现，按配置自动选：
 * <ul>
 *   <li>MockPaymentService：开发环境（app.pay.mock=true），不真扣钱</li>
 *   <li>WechatEcommercePaymentService：微信支付“电商收付通”。钱进二级商户（店铺）的冻结账户，
 *       订单完成后“完结分账”解冻到店铺余额，店铺自己提现到银行卡。平台全程不经手货款。</li>
 *   <li>DisabledPaymentService：正式环境还没开通收付通时，在线支付关闭，只能货到付款</li>
 * </ul>
 */
public interface PaymentService {

    /** 这家店现在能不能收在线支付（收付通下需要店铺已开户） */
    boolean available(Store store);

    /** 获取小程序 wx.requestPayment 参数 */
    PayParams prepay(Order order, Store store, String openId);

    /** 原路退款。受理成功返回 true（到账结果由退款回调更新） */
    boolean refund(Order order, String reason);

    /** 订单完成后把冻结的钱解冻给店铺。返回 null 成功，否则是失败原因 */
    String release(Settlement settlement);

    Balance balance(Store store);

    /** 发起提现 */
    WithdrawResult withdraw(Withdraw withdraw);

    /** 查询提现结果 */
    WithdrawResult queryWithdraw(Withdraw withdraw);

    record PayParams(
            boolean mock,
            String outTradeNo,
            String timeStamp,
            String nonceStr,
            String packageValue,
            String signType,
            String paySign) {}

    /** available 可提现，pending 还冻结着（订单未完成） */
    record Balance(BigDecimal available, BigDecimal pending) {}

    /** status：PROCESSING / SUCCESS / FAILED */
    record WithdrawResult(String status, String withdrawId, String reason) {}
}
