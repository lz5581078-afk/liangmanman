package com.liangmanman.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewView(
        Long id,
        Long orderId,
        Long storeId,
        String storeName,
        int score,
        String content,
        List<String> images,
        String nickname,
        String reply,
        LocalDateTime createdAt,
        LocalDateTime replyAt) {
}
