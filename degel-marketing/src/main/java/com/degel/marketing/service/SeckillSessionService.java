package com.degel.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.marketing.entity.SeckillSession;
import com.degel.marketing.vo.SeckillSessionCreateVo;
import com.degel.marketing.vo.SeckillSessionVo;

import java.util.List;

/**
 * 秒杀场次：平台管理（分页/新增/编辑/删除/启停）+ C 端当前场次列表（含商品）。
 */
public interface SeckillSessionService extends IService<SeckillSession> {

    /** 管理端分页：name 模糊、status 精确，按开始时间升序 */
    IPage<SeckillSession> page(Long page, Long pageSize, String name, Integer status);

    /** 新增/编辑（vo.id 空=新增，MP 自动雪花；非空=编辑，先校验存在）。校验 end > start */
    void saveOrUpdateSession(SeckillSessionCreateVo vo);

    /** 逻辑删除（@TableLogic → UPDATE del_flag=1） */
    void delete(Long id);

    /** 启/停取反（1↔0） */
    void toggleStatus(Long id);

    /**
     * C 端当前/即将开卖场次：启用且 end_time > now 且 start_time < now+24h，
     * 按 sort、startTime 升序；每场附带商品列表（sort 升序）。
     */
    List<SeckillSessionVo> listCurrent();
}
