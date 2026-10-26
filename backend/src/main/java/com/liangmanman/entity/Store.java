package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lm_store")
public class Store {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String BANNED = "BANNED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String name;
    private String phone;
    private String coverImage;
    private BigDecimal lat;
    private BigDecimal lng;
    private String addressText;
    private String town;
    private String notice;
    private String businessHours;
    private Boolean isOpen;
    private Boolean deliveryEnabled;
    private Boolean pickupEnabled;
    private BigDecimal deliveryRadiusKm;
    private BigDecimal minOrderAmount;
    private BigDecimal deliveryFee;
    private Boolean codEnabled;
    private String mainCategories;
    private String status;
    /** 拒绝或停用原因 */
    private String statusReason;
    private BigDecimal rating;
    private Integer reviewCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
