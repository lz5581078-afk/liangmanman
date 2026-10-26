package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 凑单：凑够目标数量按凑单价成交 */
@Data
@TableName("lm_group_buy")
public class GroupBuy {
    public static final String ONGOING = "ONGOING";
    public static final String SUCCESS = "SUCCESS";
    public static final String WAIT_DECISION = "WAIT_DECISION";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private Long productId;
    private BigDecimal groupPrice;
    private Integer targetQty;
    private Integer currentQty;
    private Integer limitPerUser;
    private Boolean endWhenReached;
    private LocalDateTime endAt;
    private String status;
    private LocalDateTime decisionDeadline;
    private LocalDateTime finishedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public static String statusLabel(String s) {
        return switch (s) {
            case ONGOING -> "进行中";
            case SUCCESS -> "已成团";
            case WAIT_DECISION -> "没凑够，等老板决定";
            case FAILED -> "未成团，已退款";
            default -> s;
        };
    }
}
