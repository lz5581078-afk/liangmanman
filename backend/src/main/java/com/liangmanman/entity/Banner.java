package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lm_banner")
public class Banner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String image;
    private String title;
    private String link;
    private Integer sort;
    private Boolean enabled;
    private Long regionId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
