package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lm_store_license")
public class StoreLicense {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    /** BUSINESS 营业执照 / PESTICIDE 农药经营许可证 / SEED 种子经营备案 / VET 兽药经营许可证 */
    private String type;
    private String image;
    private LocalDate validUntil;
    private String status;
    private String rejectReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public static String typeLabel(String type) {
        return switch (type) {
            case "BUSINESS" -> "营业执照";
            case "PESTICIDE" -> "农药经营许可证";
            case "SEED" -> "种子经营备案";
            case "VET" -> "兽药经营许可证";
            default -> type;
        };
    }
}
