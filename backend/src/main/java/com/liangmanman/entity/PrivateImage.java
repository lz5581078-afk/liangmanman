package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 私密图片（开户用身份证照），不对外提供访问地址 */
@Data
@TableName("lm_private_image")
public class PrivateImage {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long storeId;
    private String path;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
