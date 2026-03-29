package com.degel.app.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.app.config.AppJwtConfig;
import com.degel.app.entity.MallUser;
import com.degel.app.exception.BusinessException;
import com.degel.app.mapper.MallUserMapper;
import com.degel.app.service.impl.AuthServiceImpl;
import com.degel.app.vo.WxLoginVO;
import com.degel.app.vo.dto.LoginReqVO;
import com.degel.app.vo.dto.WxLoginReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 * <p>
 * 策略：纯 JUnit5 + Mockito，不启动 Spring 容器。
 * 所有外部依赖（Mapper / RestTemplate / PasswordEncoder）均使用 Mock，
 * @Value 字段通过 ReflectionTestUtils 注入。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    // ===== Mock 依赖 =====

    @Mock
    private MallUserMapper mallUserMapper;

    @Mock
    private AppJwtConfig appJwtConfig;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    // ===== 固定测试数据 =====

    private static final String FAKE_CODE     = "wx_code_001";
    private static final String FAKE_OPENID   = "oABC123456";
    private static final String FAKE_PHONE    = "13800138000";
    private static final String FAKE_PASSWORD = "plainText";
    private static final String FAKE_ENCODED  = "$2a$10$encodedHash";
    private static final String JWT_SECRET    = "degel-app-test-secret-key-32bytes!!";  // ≥32字节

    @BeforeEach
    void setUp() {
        // 注入 @Value 字段（替代 Spring 环境）
        ReflectionTestUtils.setField(authService, "wxAppid",    "testAppId");
        ReflectionTestUtils.setField(authService, "wxSecret",   "testSecret");
        ReflectionTestUtils.setField(authService, "wxTokenUrl", "https://fake-wx.example.com/jscode2session");

        // JWT 配置：密钥≥32字节，过期时间7天
        when(appJwtConfig.getSecret()).thenReturn(JWT_SECRET);
        when(appJwtConfig.getExpiration()).thenReturn(604800L);
    }

    // ================================================================
    // 测试用例 1：首次微信登录 → 自动注册新用户并返回 Token
    // ================================================================

    @Test
    @DisplayName("wxLogin_firstTime_shouldRegisterAndReturnToken - 首次微信登录自动注册并签发JWT")
    void wxLogin_firstTime_shouldRegisterAndReturnToken() {
        // ---- Arrange ----
        WxLoginReqVO req = new WxLoginReqVO();
        req.setCode(FAKE_CODE);
        req.setNickname("新用户昵称");
        req.setAvatar("https://cdn.example.com/avatar.jpg");

        // 微信接口返回 openid
        Map<String, Object> wxBody = new HashMap<>();
        wxBody.put("openid", FAKE_OPENID);
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(wxBody));

        // 数据库中不存在该 openid（首次登录）
        when(mallUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // insert 后 MyBatis-Plus 会回写 id，这里用 doAnswer 模拟
        doAnswer(invocation -> {
            MallUser inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(mallUserMapper).insert(any(MallUser.class));

        // ---- Act ----
        WxLoginVO result = authService.wxLogin(req);

        // ---- Assert ----
        // 1. 调用了 insert（即执行了注册逻辑）
        ArgumentCaptor<MallUser> captor = ArgumentCaptor.forClass(MallUser.class);
        verify(mallUserMapper, times(1)).insert(captor.capture());
        MallUser registered = captor.getValue();
        assertThat(registered.getOpenid()).isEqualTo(FAKE_OPENID);
        assertThat(registered.getNickname()).isEqualTo("新用户昵称");
        assertThat(registered.getStatus()).isEqualTo(0);           // 状态正常

        // 2. 返回 VO 包含非空 token 且 userId 正确
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getNickname()).isEqualTo("新用户昵称");
    }

    // ================================================================
    // 测试用例 2：封禁账号微信登录 → 抛出 BusinessException(40002)
    // ================================================================

    @Test
    @DisplayName("wxLogin_banned_shouldThrowException - 封禁账号微信登录抛40002异常")
    void wxLogin_banned_shouldThrowException() {
        // ---- Arrange ----
        WxLoginReqVO req = new WxLoginReqVO();
        req.setCode(FAKE_CODE);
        req.setNickname("被封号用户");

        // 微信接口返回 openid
        Map<String, Object> wxBody = new HashMap<>();
        wxBody.put("openid", FAKE_OPENID);
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(wxBody));

        // 数据库中存在该用户，但状态为封禁（status=1）
        MallUser bannedUser = buildNormalUser();
        bannedUser.setStatus(1);
        when(mallUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bannedUser);

        // ---- Act & Assert ----
        assertThatThrownBy(() -> authService.wxLogin(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40002);
                    assertThat(be.getMessage()).contains("封禁");
                });

        // 确认未执行 insert（不是新注册）
        verify(mallUserMapper, never()).insert(any());
    }

    // ================================================================
    // 测试用例 3：H5 密码错误 → 抛出 BusinessException(40003)
    // ================================================================

    @Test
    @DisplayName("login_wrongPassword_shouldThrowException - H5密码错误抛40003异常")
    void login_wrongPassword_shouldThrowException() {
        // ---- Arrange ----
        LoginReqVO req = new LoginReqVO();
        req.setPhone(FAKE_PHONE);
        req.setPassword("wrongPassword");

        // 数据库中存在该手机号用户，且有密码
        MallUser user = buildNormalUser();
        user.setPhone(FAKE_PHONE);
        user.setPassword(FAKE_ENCODED);
        when(mallUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        // BCrypt 校验失败（密码不匹配）
        when(passwordEncoder.matches("wrongPassword", FAKE_ENCODED)).thenReturn(false);

        // ---- Act & Assert ----
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(40003);
                    assertThat(be.getMessage()).contains("密码");
                });
    }

    // ================================================================
    // 测试用例 4：H5 登录成功 → 返回包含 JWT 的 WxLoginVO
    // ================================================================

    @Test
    @DisplayName("login_success_shouldReturnToken - H5登录成功返回JWT和用户信息")
    void login_success_shouldReturnToken() {
        // ---- Arrange ----
        LoginReqVO req = new LoginReqVO();
        req.setPhone(FAKE_PHONE);
        req.setPassword(FAKE_PASSWORD);

        MallUser user = buildNormalUser();
        user.setPhone(FAKE_PHONE);
        user.setPassword(FAKE_ENCODED);
        when(mallUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        // BCrypt 校验通过
        when(passwordEncoder.matches(FAKE_PASSWORD, FAKE_ENCODED)).thenReturn(true);

        // ---- Act ----
        WxLoginVO result = authService.login(req);

        // ---- Assert ----
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();               // JWT 已签发
        assertThat(result.getUserId()).isEqualTo(user.getId());
        assertThat(result.getNickname()).isEqualTo(user.getNickname());

        // 确认 PasswordEncoder 被调用了一次（密码校验逻辑真正执行）
        verify(passwordEncoder, times(1)).matches(FAKE_PASSWORD, FAKE_ENCODED);
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 构建一个状态正常的 MallUser（status=0，有昵称头像）
     */
    private MallUser buildNormalUser() {
        MallUser user = new MallUser();
        user.setId(1L);
        user.setOpenid(FAKE_OPENID);
        user.setNickname("测试用户");
        user.setAvatar("https://cdn.example.com/avatar.jpg");
        user.setStatus(0);
        return user;
    }
}
