package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lm_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long buyerId;
    private Long storeId;
    /** 凑单订单才有 */
    private Long groupBuyId;
    /** {@link com.liangmanman.order.OrderStatus} */
    private String status;
    /** {@link com.liangmanman.order.Fulfillment} */
    private String fulfillment;
    /** {@link com.liangmanman.order.PayMethod} */
    private String payMethod;
    private BigDecimal goodsAmount;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;
    private Boolean paid;
    private LocalDateTime paidAt;
    private Boolean codReceived;
    private Boolean reviewed;
    /** 已发出的“催接单”次数 */
    private Integer remindCount;
    private String contactName;
    private String contactPhone;
    private String addressText;
    private BigDecimal addressLat;
    private BigDecimal addressLng;
    private String village;
    private LocalDate expectedDate;
    private String remark;
    private String expressCompany;
    private String expressNo;
    private String cancelReason;
    private LocalDateTime acceptedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
