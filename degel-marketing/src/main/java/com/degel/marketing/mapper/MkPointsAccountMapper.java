package com.degel.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.marketing.entity.MkPointsAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 积分账户 Mapper。余额变动全部走带条件的原子 UPDATE（防并发超扣/负余额）。
 */
@Mapper
public interface MkPointsAccountMapper extends BaseMapper<MkPointsAccount> {

    /** 原子扣减（冻结/回收）：余额不足时影响行数=0 */
    @Update("UPDATE mk_points_account SET balance = balance - #{points} "
            + "WHERE user_id = #{userId} AND balance >= #{points}")
    int deduct(@Param("userId") Long userId, @Param("points") int points);

    /** 原子回补（入账/退回）：points 恒为正数，由调用方保证语义 */
    @Update("UPDATE mk_points_account SET balance = balance + #{points} WHERE user_id = #{userId}")
    int credit(@Param("userId") Long userId, @Param("points") int points);
}
