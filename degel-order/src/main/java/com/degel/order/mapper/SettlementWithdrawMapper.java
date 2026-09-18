package com.degel.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.order.entity.SettlementWithdraw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface SettlementWithdrawMapper extends BaseMapper<SettlementWithdraw> {

    /** 审核 CAS：仅待审核可流转（防平台并发双审），affected=0 即该单已被处理 */
    @Update("UPDATE settlement_withdraw SET status = #{status}, audit_remark = #{auditRemark}, "
            + "audit_by = #{auditBy}, audit_time = NOW(), pay_time = #{payTime} "
            + "WHERE id = #{id} AND status = 0 AND del_flag = 0")
    int casAudit(@Param("id") Long id, @Param("status") int status,
                 @Param("auditRemark") String auditRemark, @Param("auditBy") String auditBy,
                 @Param("payTime") String payTime);

    /** 平台资金总览：已打款提现合计 */
    @Select("SELECT IFNULL(SUM(amount), 0) FROM settlement_withdraw WHERE del_flag = 0 AND status = 1")
    BigDecimal sumWithdrawPaid();
}
