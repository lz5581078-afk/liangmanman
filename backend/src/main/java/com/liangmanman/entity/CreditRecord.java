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
@TableName("lm_credit_record")
public class CreditRecord {
    public static final String CREDIT = "CREDIT";
    public static final String REPAY = "REPAY";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long storeId;
    private String type;
    private BigDecimal amount;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
