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
@TableName("lm_address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String contactName;
    private String phone;
    private BigDecimal lat;
    private BigDecimal lng;
    private String town;
    private String village;
    private String detail;
    private Boolean isDefault;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 拼成一行，用于订单地址快照和送货清单 */
    public String fullText() {
        StringBuilder sb = new StringBuilder();
        if (town != null) sb.append(town);
        if (village != null) sb.append(village);
        if (detail != null) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(detail);
        }
        return sb.toString();
    }
}
