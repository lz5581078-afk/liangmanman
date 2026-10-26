package com.liangmanman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liangmanman.entity.GroupBuy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface GroupBuyMapper extends BaseMapper<GroupBuy> {

    /** 原子增减已凑数量（付款成功 +，退出凑单 -） */
    @Update("UPDATE lm_group_buy SET current_qty = current_qty + #{delta} WHERE id = #{id}")
    int addQty(@Param("id") Long id, @Param("delta") int delta);
}
