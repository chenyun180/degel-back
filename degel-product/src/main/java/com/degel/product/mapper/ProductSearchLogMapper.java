package com.degel.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.degel.product.entity.ProductSearchLog;
import com.degel.product.vo.SearchWordStatVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSearchLogMapper extends BaseMapper<ProductSearchLog> {

    /**
     * 近 N 天搜索词聚合（平台搜索词分析报表）：按词统计次数/去重人数/空结果次数/最近搜索时间。
     * LIMIT 100：报表只关心头部词，长尾无运营价值且防大结果集。
     */
    @Select("SELECT keyword, " +
            "COUNT(*) AS searchCount, " +
            "COUNT(DISTINCT user_id) AS userCount, " +
            "SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) AS zeroResultCount, " +
            "MAX(create_time) AS lastSearchTime " +
            "FROM product_search_log " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY keyword " +
            "ORDER BY searchCount DESC " +
            "LIMIT 100")
    List<SearchWordStatVo> aggregateStats(@Param("days") int days);
}
