package com.degel.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.marketing.entity.SeckillProduct;
import com.degel.marketing.vo.SeckillProductCreateVo;
import com.degel.marketing.vo.SeckillProductVo;

import java.util.List;

/**
 * 场次秒杀商品：平台管理（分页/新增/编辑/删除）+ 内部查询（场次商品列表 / 单条详情）。
 */
public interface SeckillProductService extends IService<SeckillProduct> {

    /** 管理端分页：按场次过滤，sort 升序 */
    IPage<SeckillProduct> pageBySession(Long sessionId, Long page, Long pageSize);

    /** 新增/编辑（vo.id 空=新增；非空=编辑，先校验存在）。校验同场次 skuId 唯一（排除自身） */
    void saveOrUpdateProduct(SeckillProductCreateVo vo);

    /** 逻辑删除（@TableLogic → UPDATE del_flag=1） */
    void delete(Long id);

    /** 场次下商品列表（sort 升序），/inner/seckill/current 附带用 */
    List<SeckillProductVo> listBySession(Long sessionId);

    /** 单条查询（del_flag 过滤），找不到返回 null，调用方判空 */
    SeckillProductVo getDetail(Long sessionId, Long skuId);
}
