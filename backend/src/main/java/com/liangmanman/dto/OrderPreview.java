package com.liangmanman.dto;

import java.math.BigDecimal;
import java.util.List;

/** 结算页：三种履约方式各自能不能用、为什么不能用、运费和合计、可选的付款方式 */
public record OrderPreview(
        BigDecimal goodsAmount,
        List<OrderView.Line> items,
        List<Option> options) {

    public record Option(
            String fulfillment,
            String label,
            boolean available,
            String reason,
            BigDecimal fee,
            BigDecimal total,
            List<String> payMethods) {}
}
