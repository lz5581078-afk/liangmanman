package com.liangmanman.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GroupBuyView(
        Long id,
        Long storeId,
        String storeName,
        String storePhone,
        Long productId,
        String productName,
        String productImage,
        String unit,
        BigDecimal originalPrice,
        BigDecimal groupPrice,
        int targetQty,
        int currentQty,
        Integer limitPerUser,
        LocalDateTime endAt,
        boolean endWhenReached,
        String status,
        String statusLabel,
        LocalDateTime decisionDeadline,
        List<Member> members,
        Integer myQuantity,
        Double distanceKm,
        boolean localDelivery,
        boolean expressDelivery) {

    public record Member(String nickname, String village, int quantity, LocalDateTime at) {}
}
