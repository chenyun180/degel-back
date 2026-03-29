package com.degel.app.service;

import com.degel.app.vo.AddressVO;
import com.degel.app.vo.dto.AddressCreateReqVO;
import com.degel.app.vo.dto.AddressUpdateReqVO;

import java.util.List;

/**
 * 收货地址 Service 接口
 */
public interface AddressService {

    /**
     * 新增收货地址
     * 若用户当前无地址，自动设为默认
     */
    AddressVO create(Long userId, AddressCreateReqVO req);

    /**
     * 编辑收货地址（不允许修改 isDefault）
     * 校验：del_flag=0 AND id=? AND userId=?，不存在抛 404
     */
    AddressVO update(Long userId, Long addressId, AddressUpdateReqVO req);

    /**
     * 删除收货地址（逻辑删除）
     * 若为默认地址，删除后自动将最近一条设为新默认
     */
    void delete(Long userId, Long addressId);

    /**
     * 获取收货地址列表
     * ORDER BY is_default DESC, create_time DESC
     */
    List<AddressVO> list(Long userId);

    /**
     * 设为默认地址（事务）
     * 1. 将用户所有地址 is_default=0
     * 2. 将指定地址 is_default=1
     */
    void setDefault(Long userId, Long addressId);
}
