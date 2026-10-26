package com.liangmanman.dto;

import java.math.BigDecimal;
import java.util.List;

/** 店铺页：头部信息 + 进行中的凑单 + 评价汇总 + 按分类分组的在售商品 */
public record StoreDetail(
        StoreBrief store,
        String notice,
        String businessHours,
        List<GroupBuyView> groupBuys,
        ReviewSummary reviewSummary,
        List<CategoryGroup> groups) {

    public record CategoryGroup(Long categoryId, String categoryName, List<ProductView> products) {}

    public record ReviewSummary(int count, BigDecimal rating) {}
}
