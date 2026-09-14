package com.degel.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.app.entity.MallFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品收藏 Mapper
 */
@Mapper
public interface MallFavoriteMapper extends BaseMapper<MallFavorite> {

    /**
     * 物理删除收藏记录。
     * 注意：BaseEntity.delFlag 带 @TableLogic，BaseMapper.delete() 会转成逻辑删除
     * （UPDATE del_flag=1），旧行仍占着 uk_user_spu 唯一键——再收藏时 INSERT 撞键被
     * 幂等吞掉，用户"收藏成功"但实际没收藏。取消收藏必须走本物理删除。
     */
    @Delete("DELETE FROM mall_favorite WHERE user_id = #{userId} AND spu_id = #{spuId}")
    int physicalDelete(@Param("userId") Long userId, @Param("spuId") Long spuId);

    /**
     * 插入或复活（兜底：即使库里有历史逻辑删行也能复活，原子无竞态）
     */
    @Insert("INSERT INTO mall_favorite (user_id, spu_id) VALUES (#{userId}, #{spuId}) " +
            "ON DUPLICATE KEY UPDATE del_flag = 0, update_time = NOW()")
    int insertOrRevive(@Param("userId") Long userId, @Param("spuId") Long spuId);
}
