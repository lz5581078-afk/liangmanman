package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 赊账本里的客户。balance > 0 表示还欠这么多。 */
@Data
@TableName("lm_credit_customer")
public class CreditCustomer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String name;
    private String phone;
    private String village;
    private BigDecimal balance;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
