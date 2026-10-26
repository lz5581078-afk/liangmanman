package com.liangmanman.dto;

import com.liangmanman.entity.StandardProduct;

/** 商品库里的一款货 */
public record SkuView(
        Long id,
        Long categoryId,
        String brand,
        String name,
        String spec,
        String regNo,
        String barcode,
        String manufacturer,
        String image,
        /** VERIFIED 已核实 / PENDING 待核实 */
        String status,
        /** 品牌 + 名称 + 规格 */
        String displayName) {

    public static SkuView of(StandardProduct s) {
        if (s == null) return null;
        return new SkuView(s.getId(), s.getCategoryId(), s.getBrand(), s.getName(), s.getSpec(), s.getRegNo(),
                s.getBarcode(), s.getManufacturer(), s.getImage(), s.getStatus(), s.displayName());
    }
}
