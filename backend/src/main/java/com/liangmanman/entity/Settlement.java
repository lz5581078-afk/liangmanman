package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单完成后待解冻给商家的记录；接入收付通后由结算任务调用解冻接口 */
@Data
@TableName("lm_settlement")
public class Settlement {
    public static final String PENDING_RELEASE = "PENDING_RELEASE";
    public static final String RELEASED = "RELEASED";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long storeId;
    private String subMchId;
    private String transactionId;
    private BigDecimal amount;
    private BigDecimal platformFee;
    private String status;
    private String error;
    private LocalDateTime releasedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
