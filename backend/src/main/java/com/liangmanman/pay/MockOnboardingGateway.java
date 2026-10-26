package com.liangmanman.pay;

import com.liangmanman.entity.MerchantAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** 开发环境：提交即开户成功 */
@Slf4j
@Component
@Conditional(PaymentConditions.Mock.class)
public class MockOnboardingGateway implements OnboardingGateway {

    @Override
    public String submit(Application app) {
        log.info("[模拟开户] {} 提交，简称 {}", app.outRequestNo(), app.shortName());
        return "MOCK" + app.outRequestNo();
    }

    @Override
    public Result query(String outRequestNo) {
        return new Result(MerchantAccount.APPROVED, "MOCK_" + outRequestNo, null, null);
    }
}
