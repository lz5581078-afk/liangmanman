package com.liangmanman.dto;

/** 商品页：商品 + 所属店铺 + 这是哪一款（没认出来为空）+ 还有几家店在卖同一款 */
public record ProductDetail(
        ProductView product,
        StoreBrief store,
        SkuView sku,
        long otherStoresCount) {
}
