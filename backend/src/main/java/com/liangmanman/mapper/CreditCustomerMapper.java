package com.liangmanman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liangmanman.entity.CreditCustomer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface CreditCustomerMapper extends BaseMapper<CreditCustomer> {

    /** 原子加减欠款：赊账 +，还款 - */
    @Update("UPDATE lm_credit_customer SET balance = balance + #{delta}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int addBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
