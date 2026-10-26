package com.liangmanman.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class ProductReqs {

    private ProductReqs() {}

    /**
     * 上架 / 编辑商品（编辑时整表提交）。
     * 必填只有：照片、价格。名称可空（从标准品带出，或默认“商品”）。
     * 配送方式：本地配送、快递发货至少选一项，默认只勾本地配送。
     */
    public record Save(
            @NotEmpty(message = "请拍一张商品照片") @Size(max = 5, message = "最多 5 张照片") List<@Size(max = 500) String> images,
            @NotNull(message = "请填写价格") @DecimalMin(value = "0.01", message = "价格要大于 0") @DecimalMax(value = "999999", message = "价格太高了") BigDecimal price,
            @Size(max = 64, message = "名称太长了") String name,
            Long categoryId,
            Long standardProductId,
            @Size(max = 8) String unit,
            @Min(value = 0, message = "库存不能小于 0") Integer stock,
            @Size(max = 1000) String description,
            Boolean localDelivery,
            Boolean expressDelivery,
            @DecimalMin(value = "0", message = "运费不能小于 0") BigDecimal expressFee,
            /* 以下是包装上认出来的（或老板填的）信息，用来确定是哪一款；都可空 */
            @Size(max = 32, message = "品牌太长了") String brand,
            @Size(max = 32, message = "规格太长了") String spec,
            @Size(max = 32) String barcode,
            @Size(max = 64) String regNo,
            @Size(max = 64) String manufacturer,
            /** 老板点了“不是这一款”：编辑时去掉原来挂的款 */
            Boolean notThisSku) {}
}
