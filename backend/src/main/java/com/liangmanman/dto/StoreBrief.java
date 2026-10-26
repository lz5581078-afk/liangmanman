package com.liangmanman.dto;

import com.liangmanman.entity.Store;

import java.math.BigDecimal;

/**
 * 店铺卡片（附近列表、搜索结果、店铺页头部共用）。
 * distanceKm / canDeliver 只在传了买家位置时有值。
 */
public record StoreBrief(
        Long id,
        String name,
        String coverImage,
        String phone,
        boolean isOpen,
        String town,
        String addressText,
        BigDecimal lat,
        BigDecimal lng,
        String mainCategories,
        BigDecimal rating,
        int reviewCount,
        boolean deliveryEnabled,
        boolean pickupEnabled,
        BigDecimal deliveryRadiusKm,
        BigDecimal deliveryFee,
        BigDecimal minOrderAmount,
        boolean codEnabled,
        Double distanceKm,
        Boolean canDeliver) {

    public static StoreBrief of(Store s, Double distanceKm) {
        Boolean canDeliver = null;
        if (distanceKm != null) {
            canDeliver = Boolean.TRUE.equals(s.getDeliveryEnabled())
                    && distanceKm <= s.getDeliveryRadiusKm().doubleValue();
        }
        return new StoreBrief(
                s.getId(), s.getName(), s.getCoverImage(), s.getPhone(),
                Boolean.TRUE.equals(s.getIsOpen()), s.getTown(), s.getAddressText(),
                s.getLat(), s.getLng(), s.getMainCategories(), s.getRating(),
                s.getReviewCount() == null ? 0 : s.getReviewCount(),
                Boolean.TRUE.equals(s.getDeliveryEnabled()), Boolean.TRUE.equals(s.getPickupEnabled()),
                s.getDeliveryRadiusKm(), s.getDeliveryFee(), s.getMinOrderAmount(),
                Boolean.TRUE.equals(s.getCodEnabled()), distanceKm, canDeliver);
    }
}
