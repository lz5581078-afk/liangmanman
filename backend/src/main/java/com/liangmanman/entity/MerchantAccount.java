package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 店铺的收款账户（微信支付收付通二级商户）。只保存打码后的个人信息。 */
@Data
@TableName("lm_merchant_account")
public class MerchantAccount {
    public static final String NONE = "NONE";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String AUDITING = "AUDITING";
    public static final String NEED_SIGN = "NEED_SIGN";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long storeId;
    private String applymentNo;
    private String applymentId;
    private String subMchId;
    private String status;
    private String bankCardMasked;
    private String idNameMasked;
    private String signUrl;
    private String rejectReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static String statusLabel(String s) {
        return switch (s) {
            case NONE -> "未开通";
            case SUBMITTED -> "已提交";
            case AUDITING -> "审核中";
            case NEED_SIGN -> "待签约";
            case APPROVED -> "已开通";
            case REJECTED -> "未通过";
            default -> s;
        };
    }
}
