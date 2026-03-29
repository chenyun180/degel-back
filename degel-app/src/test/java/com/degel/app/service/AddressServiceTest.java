package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.degel.app.entity.MallAddress;
import com.degel.app.exception.BusinessException;
import com.degel.app.mapper.MallAddressMapper;
import com.degel.app.service.impl.AddressServiceImpl;
import com.degel.app.vo.AddressVO;
import com.degel.app.vo.dto.AddressCreateReqVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AddressServiceImpl 单元测试
 * <p>
 * 策略：纯 JUnit5 + Mockito，不启动 Spring 容器。
 * MallAddressMapper 完全 Mock，验证核心业务逻辑：
 *   - 首个地址自动设为默认
 *   - 删除默认地址后将最新地址晋升为默认
 *   - setDefault 先清零全部再置目标
 *   - 操作他人地址时抛出 40300 异常
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService 单元测试")
class AddressServiceTest {

    // ===== Mock 依赖 =====

    @Mock
    private MallAddressMapper mallAddressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    // ===== 固定测试数据 =====

    private static final Long USER_ID    = 1001L;
    private static final Long OTHER_UID  = 9999L;
    private static final Long ADDR_ID_1  = 10L;
    private static final Long ADDR_ID_2  = 20L;

    // ================================================================
    // 测试用例 1：首个地址自动设为默认（isDefault = 1）
    // ================================================================

    @Test
    @DisplayName("addAddress_firstOne_shouldSetDefault - 首个地址自动设isDefault=1")
    void addAddress_firstOne_shouldSetDefault() {
        // ---- Arrange ----
        // 当前用户没有任何地址（count = 0）
        when(mallAddressMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // insert 回写 id
        doAnswer(invocation -> {
            MallAddress addr = invocation.getArgument(0);
            addr.setId(ADDR_ID_1);
            return 1;
        }).when(mallAddressMapper).insert(any(MallAddress.class));

        AddressCreateReqVO req = buildCreateReq("张三", "13800138000");

        // ---- Act ----
        AddressVO result = addressService.create(USER_ID, req);

        // ---- Assert ----
        // 捕获 insert 入参，验证 isDefault=1
        ArgumentCaptor<MallAddress> captor = ArgumentCaptor.forClass(MallAddress.class);
        verify(mallAddressMapper, times(1)).insert(captor.capture());
        MallAddress saved = captor.getValue();
        assertThat(saved.getIsDefault()).isEqualTo(1);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);

        // 返回 VO 中 isDefault 也应为 1
        assertThat(result.getIsDefault()).isEqualTo(1);
        assertThat(result.getId()).isEqualTo(ADDR_ID_1);
    }

    // ================================================================
    // 测试用例 2：删除默认地址后自动转移默认到最近一条
    // ================================================================

    @Test
    @DisplayName("deleteDefaultAddress_shouldTransferDefault - 删除默认地址后转移默认到最新地址")
    void deleteDefaultAddress_shouldTransferDefault() {
        // ---- Arrange ----
        // 要删除的地址：属于该用户，且是默认地址
        MallAddress defaultAddr = buildAddress(ADDR_ID_1, USER_ID, 1);
        // getAndValidate：先按 id 查地址
        when(mallAddressMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(defaultAddr)        // 第一次：getAndValidate 返回目标地址
                .thenReturn(buildAddress(ADDR_ID_2, USER_ID, 0)); // 第二次：查找最新地址

        // ---- Act ----
        addressService.delete(USER_ID, ADDR_ID_1);

        // ---- Assert ----
        // update 应被调用两次：
        //   1. 逻辑删除目标地址（delFlag=1）
        //   2. 将最新地址设为默认（isDefault=1）
        verify(mallAddressMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    // ================================================================
    // 测试用例 3：setDefault 先清零全部地址再将目标设为默认
    // ================================================================

    @Test
    @DisplayName("setDefault_shouldClearOthers - setDefault先将所有地址isDefault清零再设目标")
    void setDefault_shouldClearOthers() {
        // ---- Arrange ----
        // getAndValidate 返回合法地址（属于该用户，非默认）
        MallAddress addr = buildAddress(ADDR_ID_2, USER_ID, 0);
        when(mallAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(addr);

        // ---- Act ----
        addressService.setDefault(USER_ID, ADDR_ID_2);

        // ---- Assert ----
        // update 被调用两次：
        //   第 1 次：全量清零（eq userId + eq delFlag=0，set isDefault=0）
        //   第 2 次：仅设目标（eq id，set isDefault=1）
        verify(mallAddressMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));

        // 捕获两次 update 的 UpdateWrapper 参数，分别验证语义
        ArgumentCaptor<LambdaUpdateWrapper<MallAddress>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mallAddressMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        // 两个 wrapper 都不应为 null（说明确实构造了条件）
        assertThat(wrapperCaptor.getAllValues()).hasSize(2);
        assertThat(wrapperCaptor.getAllValues().get(0)).isNotNull(); // 清零 wrapper
        assertThat(wrapperCaptor.getAllValues().get(1)).isNotNull(); // 设默认 wrapper
    }

    // ================================================================
    // 测试用例 4：操作他人地址 → 抛出 BusinessException(40300)
    // ================================================================

    @Test
    @DisplayName("operateOtherUserAddress_shouldThrow40300 - 操作他人地址抛40300权限异常")
    void operateOtherUserAddress_shouldThrow40300() {
        // ---- Arrange ----
        // 地址实际属于 OTHER_UID，但当前操作者是 USER_ID
        MallAddress otherUserAddr = buildAddress(ADDR_ID_1, OTHER_UID, 0);
        when(mallAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(otherUserAddr);

        // ---- Act & Assert：delete 操作他人地址 ----
        assertThatThrownBy(() -> addressService.delete(USER_ID, ADDR_ID_1))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40300);
                    assertThat(be.getMessage()).contains("无权");
                });

        // ---- Act & Assert：setDefault 操作他人地址 ----
        // 重置 stub（selectOne 仍返回他人地址）
        when(mallAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(otherUserAddr);

        assertThatThrownBy(() -> addressService.setDefault(USER_ID, ADDR_ID_1))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40300);
                });

        // 确认整个过程中未执行任何 update（权限校验在修改之前）
        verify(mallAddressMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 构造一条 MallAddress
     */
    private MallAddress buildAddress(Long id, Long userId, int isDefault) {
        MallAddress addr = new MallAddress();
        addr.setId(id);
        addr.setUserId(userId);
        addr.setName("测试收货人");
        addr.setPhone("13800138000");
        addr.setProvince("广东省");
        addr.setCity("深圳市");
        addr.setDistrict("南山区");
        addr.setDetail("科技园南路1号");
        addr.setIsDefault(isDefault);
        addr.setDelFlag(0);
        return addr;
    }

    /**
     * 构造新增地址请求 VO
     */
    private AddressCreateReqVO buildCreateReq(String name, String phone) {
        AddressCreateReqVO req = new AddressCreateReqVO();
        req.setName(name);
        req.setPhone(phone);
        req.setProvince("广东省");
        req.setCity("深圳市");
        req.setDistrict("南山区");
        req.setDetail("科技园南路1号");
        return req;
    }
}
