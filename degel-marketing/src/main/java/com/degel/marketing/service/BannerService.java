package com.degel.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.degel.marketing.entity.Banner;
import com.degel.marketing.vo.BannerCreateVo;

import java.util.List;

/**
 * 营销轮播图：平台管理（分页/新增/编辑/删除/上下架）+ C 端生效列表。
 */
public interface BannerService extends IService<Banner> {

    /** 管理端分页：title 模糊、status 精确，按创建时间倒序 */
    IPage<Banner> page(Long page, Long pageSize, String title, Integer status);

    /** 新增/编辑（vo.id 空=新增，MP 自动雪花；非空=编辑，先校验存在） */
    void saveOrUpdateBanner(BannerCreateVo vo);

    /** 逻辑删除（@TableLogic → UPDATE del_flag=1） */
    void delete(Long id);

    /** 上/下架取反（1↔0） */
    void toggleStatus(Long id);

    /** C 端生效列表：上架且在有效期内（NULL 起止视为立即/永久），sort 升序，最多 10 条 */
    List<Banner> listActive();
}
