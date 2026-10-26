package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 开通区域：圆心 + 半径。有启用的区域时，只有区域内能开店。 */
@Data
@TableName("lm_region")
public class Region {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private BigDecimal lat;
    private BigDecimal lng;
    private BigDecimal radiusKm;
    private Boolean enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
