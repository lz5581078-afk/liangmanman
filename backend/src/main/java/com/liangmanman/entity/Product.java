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
@TableName("lm_product")
public class Product {
    public static final String LINKED = "LINKED";
    public static final String PENDING = "PENDING";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long standardProductId;
    private Long categoryId;
    private String name;
    /** 品牌、规格：上架识别带出或老板填写，列表里显示 */
    private String brand;
    private String spec;
    private BigDecimal price;
    private String unit;
    /** JSON 数组字符串 */
    private String images;
    private String description;
    /** null = 不限量 */
    private Integer stock;
    private Integer sales;
    /** 支持本地配送（送货上门 / 到店自提） */
    private Boolean localDelivery;
    /** 支持快递发货 */
    private Boolean expressDelivery;
    private BigDecimal expressFee;
    private Boolean onShelf;
    /** 平台下架原因 */
    private String offReason;
    private String normalizeStatus;
    /** 搜索用：名称拼音全拼、首字母、标准品同义词 */
    private String searchText;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
