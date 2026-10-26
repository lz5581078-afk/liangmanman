package com.liangmanman.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 搜索结果：像淘宝一样把所有商品平铺出来（每家店的每个商品一条，可翻页）。
 * 只有精准找货（搜的是条码 / 登记证号）时，exact 里才给出“这一款”在各家店的比价。
 */
public record SearchResult(
        String keyword,
        long total,
        long page,
        long size,
        List<Item> records,
        /** 精准命中的款及各店报价；不是精准找货时为空数组 */
        List<SkuOffers> exact) {

    /** 列表里的一条商品 */
    public record Item(
            Long productId,
            String name,
            String brand,
            String spec,
            String image,
            BigDecimal price,
            String unit,
            Integer sales,
            Long storeId,
            String storeName,
            boolean storeOpen,
            Double distanceKm,
            /** 本地：能送货上门到买家位置 */
            boolean canDeliver,
            /** 本地：能到店自提 */
            boolean canPickup,
            /** 支持快递发货 */
            boolean express,
            BigDecimal expressFee,
            BigDecimal deliveryFee,
            /** 属于商品库里的哪一款，没认出为空 */
            Long skuId,
            /** 附近（含快递可达）卖同一款的店数，含本店；>1 时可点“同款比价” */
            int sameCount) {}

    /** 同一款在各家店的报价 */
    public record SkuOffers(SkuView sku, BigDecimal minPrice, List<Offer> offers) {}

    public record Offer(
            Long productId,
            String productName,
            BigDecimal price,
            String unit,
            String image,
            Long storeId,
            String storeName,
            boolean storeOpen,
            Double distanceKm,
            boolean canDeliver,
            boolean canPickup,
            boolean express,
            BigDecimal expressFee,
            BigDecimal deliveryFee) {}
}
