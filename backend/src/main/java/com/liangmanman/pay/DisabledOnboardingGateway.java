package com.liangmanman.pay;

import com.liangmanman.common.BizException;
import com.liangmanman.entity.MerchantAccount;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** 平台还没开通收付通：不能开户，只能货到付款 */
@Component
@Conditional(PaymentConditions.Disabled.class)
public class DisabledOnboardingGateway implements OnboardingGateway {

    @Override
    public String submit(Application app) {
        throw new BizException("平台的在线收款还在开通中，暂时只能货到付款，开通后会通知你");
    }

    @Override
    public Result query(String outRequestNo) {
        return new Result(MerchantAccount.AUDITING, null, null, null);
    }
}
