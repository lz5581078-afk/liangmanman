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
@TableName("lm_wanted_reply")
public class WantedReply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wantedId;
    private Long storeId;
    private BigDecimal price;
    private String note;
    private Long productId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
