package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 求购：农户搜不到时发布，附近的店可以回复 */
@Data
@TableName("lm_wanted")
public class Wanted {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String keyword;
    private String note;
    private String contactPhone;
    private BigDecimal lat;
    private BigDecimal lng;
    private String status;
    private LocalDateTime expireAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
