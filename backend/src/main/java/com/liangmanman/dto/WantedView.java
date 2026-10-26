package com.liangmanman.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WantedView(
        Long id,
        String keyword,
        String note,
        String contactPhone,
        /** OPEN / CLOSED / EXPIRED */
        String status,
        LocalDateTime createdAt,
        LocalDateTime expireAt,
        Double distanceKm,
        List<Reply> replies) {

    public record Reply(Long id, Long storeId, String storeName, String storePhone,
                        BigDecimal price, String note, Long productId, LocalDateTime createdAt) {}
}
