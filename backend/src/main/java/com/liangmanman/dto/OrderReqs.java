package com.liangmanman.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class OrderReqs {

    private OrderReqs() {}

    public record Item(
            @NotNull Long productId,
            @NotNull @Min(value = 1, message = "数量至少 1") @Max(value = 9999, message = "数量太多了") Integer quantity) {}

    /** 结算页预览：返回三种履约方式哪些能用、运费多少 */
    public record Preview(
            @NotNull Long storeId,
            @NotEmpty(message = "购物车是空的") @Size(max = 50) List<@Valid Item> items,
            Long addressId,
            /** 参加凑单时传 */
            Long groupBuyId) {}

    public record Create(
            @NotNull Long storeId,
            @NotEmpty(message = "购物车是空的") @Size(max = 50) List<@Valid Item> items,
            @NotBlank(message = "请选择配送方式") @Pattern(regexp = "DELIVERY|PICKUP|EXPRESS", message = "配送方式不对") String fulfillment,
            @NotBlank(message = "请选择付款方式") @Pattern(regexp = "ONLINE|COD", message = "付款方式不对") String payMethod,
            /** 送货上门、快递必填；自提可不填 */
            Long addressId,
            /** 自提且没填地址时的联系人 */
            @Size(max = 32) String contactName,
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不对") String contactPhone,
            LocalDate expectedDate,
            @Size(max = 200, message = "备注太长了") String remark,
            /** 参加凑单时传 */
            Long groupBuyId) {}

    public record Reason(@Size(max = 200) String reason) {}

    public record Ship(
            @NotBlank(message = "请填写快递公司") @Size(max = 32) String expressCompany,
            @NotBlank(message = "请填写快递单号") @Size(max = 64) String expressNo) {}
}
