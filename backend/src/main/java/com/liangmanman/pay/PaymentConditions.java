package com.liangmanman.pay;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 按配置决定用哪一种支付实现 */
public final class PaymentConditions {

    private PaymentConditions() {}

    static boolean mock(ConditionContext ctx) {
        return Boolean.parseBoolean(ctx.getEnvironment().getProperty("app.pay.mock", "false"));
    }

    static boolean wechat(ConditionContext ctx) {
        String mch = ctx.getEnvironment().getProperty("app.pay.wechat.sp-mch-id", "");
        return !mock(ctx) && mch != null && !mch.isBlank();
    }

    public static class Mock implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            return mock(ctx);
        }
    }

    public static class Wechat implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            return wechat(ctx);
        }
    }

    public static class Disabled implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            return !mock(ctx) && !wechat(ctx);
        }
    }
}
