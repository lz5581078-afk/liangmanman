package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lm_notify_log")
public class NotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** SMS / VOICE / SUBSCRIBE */
    private String channel;
    private String target;
    private String content;
    private Boolean success;
    private String error;
    private Long orderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
