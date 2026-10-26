package com.liangmanman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liangmanman.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    /** 扣库存：只在库存足够时成功，返回 0 表示库存不足。不限量（stock 为 NULL）的商品不要调用。 */
    @Update("UPDATE lm_product SET stock = stock - #{qty} WHERE id = #{id} AND stock IS NOT NULL AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    @Update("UPDATE lm_product SET stock = stock + #{qty} WHERE id = #{id} AND stock IS NOT NULL")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    @Update("UPDATE lm_product SET sales = sales + #{qty} WHERE id = #{id}")
    int addSales(@Param("id") Long id, @Param("qty") int qty);
}
