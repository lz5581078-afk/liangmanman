package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lm_review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long storeId;
    private Long userId;
    private Integer score;
    private String content;
    /** JSON 数组 */
    private String images;
    private String reply;
    private LocalDateTime replyAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
