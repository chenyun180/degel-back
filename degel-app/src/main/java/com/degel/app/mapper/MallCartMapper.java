package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper
 */
@Mapper
public interface MallCartMapper extends BaseMapper<MallCart> {

    /**
     * 批量逻辑删除购物车记录（只删除属于该用户的）
     *
     * @param ids    购物车 ID 列表
     * @param userId 用户 ID
     * @return 影响行数
     */
    int deleteByIdsAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    /**
     * 原子累加购物车商品数量（防止并发 TOCTOU 竞态）
     *
     * @param id       购物车记录 ID
     * @param userId   用户 ID（防越权）
     * @param quantity 本次增加的数量
     * @return 影响行数
     */
    int incrementQuantity(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") Integer quantity);

    /**
     * 插入或复活购物车记录（uk_user_sku 唯一键 + 逻辑删共存场景）：
     * 新记录正常插入；撞到已逻辑删的旧记录则复活并重置数量
     */
    int insertOrRevive(@Param("userId") Long userId, @Param("spuId") Long spuId,
                       @Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
