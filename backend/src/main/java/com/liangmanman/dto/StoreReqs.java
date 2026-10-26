package com.liangmanman.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class StoreReqs {

    private StoreReqs() {}

    /** 开店：只问最少的信息，其余用默认值，开店后再在“店铺设置”里改 */
    public record Open(
            @NotBlank(message = "请填写店名") @Size(max = 64, message = "店名太长了") String name,
            @NotBlank(message = "请填写电话") @Pattern(regexp = "^[0-9\\-]{7,20}$", message = "电话格式不对") String phone,
            @Size(max = 500) String coverImage,
            @NotNull(message = "请在地图上选店铺位置") @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @NotNull(message = "请在地图上选店铺位置") @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @Size(max = 200) String addressText,
            @Size(max = 32) String town,
            @Size(max = 200) String mainCategories) {}

    /** 店铺设置：传了哪个字段就改哪个 */
    public record Update(
            @Size(max = 64) String name,
            @Pattern(regexp = "^[0-9\\-]{7,20}$", message = "电话格式不对") String phone,
            @Size(max = 500) String coverImage,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal lng,
            @Size(max = 200) String addressText,
            @Size(max = 32) String town,
            @Size(max = 500) String notice,
            @Size(max = 64) String businessHours,
            Boolean isOpen,
            Boolean deliveryEnabled,
            Boolean pickupEnabled,
            @DecimalMin(value = "0.5", message = "配送范围至少 0.5 公里") @DecimalMax(value = "100", message = "配送范围最多 100 公里") BigDecimal deliveryRadiusKm,
            @DecimalMin(value = "0", message = "起送金额不能小于 0") BigDecimal minOrderAmount,
            @DecimalMin(value = "0", message = "配送费不能小于 0") BigDecimal deliveryFee,
            Boolean codEnabled,
            @Size(max = 200) String mainCategories) {}
}
