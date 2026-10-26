package com.liangmanman.dto;

import com.liangmanman.common.JsonUtil;
import com.liangmanman.entity.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductView(
        Long id,
        Long storeId,
        Long categoryId,
        Long standardProductId,
        String name,
        String brand,
        String spec,
        BigDecimal price,
        String unit,
        List<String> images,
        String description,
        Integer stock,
        Integer sales,
        boolean localDelivery,
        boolean expressDelivery,
        BigDecimal expressFee,
        boolean onShelf,
        /** 平台下架原因 */
        String offReason) {

    public static ProductView of(Product p) {
        return new ProductView(
                p.getId(), p.getStoreId(), p.getCategoryId(), p.getStandardProductId(),
                p.getName(), p.getBrand(), p.getSpec(), p.getPrice(), p.getUnit(), JsonUtil.toStringList(p.getImages()),
                p.getDescription(), p.getStock(), p.getSales(),
                Boolean.TRUE.equals(p.getLocalDelivery()),
                Boolean.TRUE.equals(p.getExpressDelivery()),
                p.getExpressFee(),
                Boolean.TRUE.equals(p.getOnShelf()),
                p.getOffReason());
    }

    public String firstImage() {
        return images == null || images.isEmpty() ? null : images.get(0);
    }
}
