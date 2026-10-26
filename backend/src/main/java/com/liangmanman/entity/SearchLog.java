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
@TableName("lm_search_log")
public class SearchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private Integer resultCount;
    private Long userId;
    private BigDecimal lat;
    private BigDecimal lng;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
