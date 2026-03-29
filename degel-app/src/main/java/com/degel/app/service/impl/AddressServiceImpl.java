package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.app.entity.MallAddress;
import com.degel.app.exception.BusinessException;
import com.degel.app.mapper.MallAddressMapper;
import com.degel.app.service.AddressService;
import com.degel.app.vo.AddressVO;
import com.degel.app.vo.dto.AddressCreateReqVO;
import com.degel.app.vo.dto.AddressUpdateReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final MallAddressMapper mallAddressMapper;

    // ===== A-05: POST /app/user/address =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO create(Long userId, AddressCreateReqVO req) {
        // 检查当前用户是否有地址，若无则自动设为默认
        Long existCount = mallAddressMapper.selectCount(
                new LambdaQueryWrapper<MallAddress>()
                        .eq(MallAddress::getUserId, userId)
                        .eq(MallAddress::getDelFlag, 0)
        );
        boolean isFirstAddress = existCount == 0;

        MallAddress address = new MallAddress();
        address.setUserId(userId);
        address.setName(req.getName());
        address.setPhone(req.getPhone());
        address.setProvince(req.getProvince());
        address.setCity(req.getCity());
        address.setDistrict(req.getDistrict());
        address.setDetail(req.getDetail());
        address.setIsDefault(isFirstAddress ? 1 : 0);
        address.setDelFlag(0);
        address.setCreateTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());

        mallAddressMapper.insert(address);
        log.info("新增收货地址成功, userId={}, addressId={}, isDefault={}", userId, address.getId(), address.getIsDefault());

        return toVO(address);
    }

    // ===== A-06: PUT /app/user/address/{id} =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO update(Long userId, Long addressId, AddressUpdateReqVO req) {
        MallAddress address = getAndValidate(userId, addressId);

        // 更新非空字段（isDefault 不在此处修改）
        if (StringUtils.hasText(req.getName())) {
            address.setName(req.getName());
        }
        if (StringUtils.hasText(req.getPhone())) {
            address.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getProvince())) {
            address.setProvince(req.getProvince());
        }
        if (StringUtils.hasText(req.getCity())) {
            address.setCity(req.getCity());
        }
        if (StringUtils.hasText(req.getDistrict())) {
            address.setDistrict(req.getDistrict());
        }
        if (StringUtils.hasText(req.getDetail())) {
            address.setDetail(req.getDetail());
        }
        address.setUpdateTime(LocalDateTime.now());

        mallAddressMapper.updateById(address);
        return toVO(address);
    }

    // ===== A-07: DELETE /app/user/address/{id} =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long addressId) {
        MallAddress address = getAndValidate(userId, addressId);
        boolean wasDefault = Integer.valueOf(1).equals(address.getIsDefault());

        // 逻辑删除
        mallAddressMapper.update(null,
                new LambdaUpdateWrapper<MallAddress>()
                        .eq(MallAddress::getId, addressId)
                        .eq(MallAddress::getUserId, userId)
                        .set(MallAddress::getDelFlag, 1)
                        .set(MallAddress::getUpdateTime, LocalDateTime.now())
        );

        // 若删除的是默认地址，将最近一条设为新默认
        if (wasDefault) {
            MallAddress newest = mallAddressMapper.selectOne(
                    new LambdaQueryWrapper<MallAddress>()
                            .eq(MallAddress::getUserId, userId)
                            .eq(MallAddress::getDelFlag, 0)
                            .orderByDesc(MallAddress::getCreateTime)
                            .last("LIMIT 1")
            );
            if (newest != null) {
                mallAddressMapper.update(null,
                        new LambdaUpdateWrapper<MallAddress>()
                                .eq(MallAddress::getId, newest.getId())
                                .set(MallAddress::getIsDefault, 1)
                                .set(MallAddress::getUpdateTime, LocalDateTime.now())
                );
                log.info("删除默认地址后，自动设置新默认地址, userId={}, newDefaultId={}", userId, newest.getId());
            }
        }

        log.info("删除收货地址成功, userId={}, addressId={}", userId, addressId);
    }

    // ===== A-08: GET /app/user/address =====

    @Override
    public List<AddressVO> list(Long userId) {
        List<MallAddress> addresses = mallAddressMapper.selectList(
                new LambdaQueryWrapper<MallAddress>()
                        .eq(MallAddress::getUserId, userId)
                        .eq(MallAddress::getDelFlag, 0)
                        .orderByDesc(MallAddress::getIsDefault)
                        .orderByDesc(MallAddress::getCreateTime)
        );
        return addresses.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ===== A-09: PUT /app/user/address/{id}/default =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        // 校验地址存在且属于该用户
        getAndValidate(userId, addressId);

        // Step 1: 将该用户所有地址设为非默认
        mallAddressMapper.update(null,
                new LambdaUpdateWrapper<MallAddress>()
                        .eq(MallAddress::getUserId, userId)
                        .eq(MallAddress::getDelFlag, 0)
                        .set(MallAddress::getIsDefault, 0)
                        .set(MallAddress::getUpdateTime, LocalDateTime.now())
        );

        // Step 2: 将指定地址设为默认
        mallAddressMapper.update(null,
                new LambdaUpdateWrapper<MallAddress>()
                        .eq(MallAddress::getId, addressId)
                        .set(MallAddress::getIsDefault, 1)
                        .set(MallAddress::getUpdateTime, LocalDateTime.now())
        );

        log.info("设置默认地址成功, userId={}, addressId={}", userId, addressId);
    }

    // ===== 私有工具方法 =====

    /**
     * 查询并校验地址的归属权
     * @throws BusinessException 地址不存在 or 无权操作
     */
    private MallAddress getAndValidate(Long userId, Long addressId) {
        MallAddress address = mallAddressMapper.selectOne(
                new LambdaQueryWrapper<MallAddress>()
                        .eq(MallAddress::getId, addressId)
                        .eq(MallAddress::getDelFlag, 0)
        );

        if (address == null) {
            throw BusinessException.addressNotFound();
        }

        if (!address.getUserId().equals(userId)) {
            throw BusinessException.addressForbidden();
        }

        return address;
    }

    /**
     * Entity → VO 转换（fullAddress 由 getFullAddress() 动态计算）
     */
    private AddressVO toVO(MallAddress address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setUserId(address.getUserId());
        vo.setName(address.getName());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        // fullAddress 由 AddressVO.getFullAddress() 动态拼接
        return vo;
    }
}
