package com.degel.app.service;

import com.degel.app.filter.InnerTokenFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * InnerTokenFilter 单元测试
 *
 * <p>覆盖用例：
 * 1. validToken_shouldPassThrough    — 正确的 X-Inner-Token 放行请求
 * 2. invalidToken_shouldReturn403    — 错误的 token 返回 403
 * 3. noToken_shouldReturn403         — 无 token 请求返回 403
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InnerTokenFilter 单元测试")
class InnerTokenFilterTest {

    private static final String INNER_PATH   = "/app/inner/pay/refund";
    private static final String NON_INNER_PATH = "/app/order/list";
    private static final String VALID_TOKEN  = "degel-inner-service-token-2024";
    private static final String INVALID_TOKEN = "wrong-token-xyz";
    private static final String TOKEN_HEADER = "X-Inner-Token";

    private InnerTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InnerTokenFilter();
        // 通过 ReflectionTestUtils 注入 @Value 字段（替代 Spring 容器）
        ReflectionTestUtils.setField(filter, "innerToken", VALID_TOKEN);
    }

    // ======================================================================
    // 用例 1：正确 token，内部路径放行
    // ======================================================================

    @Test
    @DisplayName("validToken_shouldPassThrough — 携带正确 X-Inner-Token 时请求被放行")
    void validToken_shouldPassThrough() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", INNER_PATH);
        request.addHeader(TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilterInternal(request, response, chain);

        // then: 响应状态仍为默认 200，chain 被调用（请求已透传）
        assertThat(response.getStatus()).isEqualTo(200);
        // MockFilterChain 被执行后，getRequest() 不为 null 表示 doFilter 被调用
        assertThat(chain.getRequest()).isNotNull();
    }

    // ======================================================================
    // 用例 2：错误 token，内部路径返回 403
    // ======================================================================

    @Test
    @DisplayName("invalidToken_shouldReturn403 — 携带错误 X-Inner-Token 时返回 403")
    void invalidToken_shouldReturn403() throws ServletException, IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", INNER_PATH);
        request.addHeader(TOKEN_HEADER, INVALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        // when
        filter.doFilterInternal(request, response, chain);

        // then: 返回 403，chain.doFilter 未被调用
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("403");
        verify(chain, never()).doFilter(any(), any());
    }

    // ======================================================================
    // 用例 3：无 token，内部路径返回 403
    // ======================================================================

    @Test
    @DisplayName("noToken_shouldReturn403 — 未携带 X-Inner-Token 时返回 403")
    void noToken_shouldReturn403() throws ServletException, IOException {
        // given: 内部路径，但不携带任何 token header
        MockHttpServletRequest request = new MockHttpServletRequest("POST", INNER_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = mock(MockFilterChain.class);

        // when
        filter.doFilterInternal(request, response, chain);

        // then: 返回 403，chain.doFilter 未被调用
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("鉴权失败");
        verify(chain, never()).doFilter(any(), any());
    }
}
