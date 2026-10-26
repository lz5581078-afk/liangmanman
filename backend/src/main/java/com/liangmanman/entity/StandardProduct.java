package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品库：具体到“哪个牌子、哪个规格、哪个登记证号”的一款货。
 * 各店卖的同一款挂到这里，才算“同款”，才能比价。
 * 条码相同、或登记证号 + 规格都相同，就是同一款。
 */
@Data
@TableName("lm_standard_product")
public class StandardProduct {
    public static final String VERIFIED = "VERIFIED";
    public static final String PENDING = "PENDING";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String brand;
    private String name;
    private String spec;
    private String regNo;
    private String barcode;
    private String manufacturer;
    private String image;
    private String keywords;
    /** VERIFIED 已核实 / PENDING 商家上架时自动建档，待运营核实 */
    private String status;
    private Long sourceStoreId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public String displayName() {
        StringBuilder sb = new StringBuilder();
        if (brand != null && !brand.isBlank()) sb.append(brand).append(' ');
        sb.append(name);
        if (spec != null && !spec.isBlank()) sb.append(' ').append(spec);
        return sb.toString();
    }
}
