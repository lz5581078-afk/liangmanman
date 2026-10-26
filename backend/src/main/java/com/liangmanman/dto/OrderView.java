package com.liangmanman.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情 / 列表项。
 * buyerActions / merchantActions 告诉前端此刻能点哪些按钮，前端不自己判断状态。
 */
public record OrderView(
        Long id,
        String orderNo,
        Long groupBuyId,
        boolean reviewed,
        String status,
        String statusLabel,
        String fulfillment,
        String fulfillmentLabel,
        String payMethod,
        String payMethodLabel,
        BigDecimal goodsAmount,
        BigDecimal deliveryFee,
        BigDecimal totalAmount,
        boolean paid,
        boolean codReceived,
        String contactName,
        String contactPhone,
        String addressText,
        BigDecimal addressLat,
        BigDecimal addressLng,
        String village,
        LocalDate expectedDate,
        String remark,
        String expressCompany,
        String expressNo,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime completedAt,
        Long storeId,
        String storeName,
        String storePhone,
        List<Line> items,
        List<String> buyerActions,
        List<String> merchantActions,
        List<Log> logs) {

    public record Line(Long productId, String name, String image, String unit,
                       BigDecimal price, Integer quantity, BigDecimal amount) {}

    public record Log(String status, String statusLabel, String operator, String note, LocalDateTime at) {}
}
