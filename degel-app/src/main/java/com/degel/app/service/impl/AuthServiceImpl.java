package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.degel.app.config.AppJwtConfig;
import com.degel.app.entity.MallUser;
import com.degel.app.exception.BusinessException;
import com.degel.app.mapper.MallUserMapper;
import com.degel.app.service.AuthService;
import com.degel.app.vo.WxLoginVO;
import com.degel.app.vo.dto.LoginReqVO;
import com.degel.app.vo.dto.WxLoginReqVO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final MallUserMapper mallUserMapper;
    private final AppJwtConfig appJwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;

    @PostConstruct
    public void validateSecret() {
        Assert.isTrue(appJwtConfig.getSecret().getBytes(StandardCharsets.UTF_8).length >= 32,
            "degel.app.jwt.secret 长度不得少于32字节，当前=" + appJwtConfig.getSecret().length() + "字符");
    }

    @Value("${degel.wx.appid}")
    private String wxAppid;

    @Value("${degel.wx.secret}")
    private String wxSecret;

    @Value("${degel.wx.token-url}")
    private String wxTokenUrl;

    // ===== 微信登录 =====

    @Override
    public WxLoginVO wxLogin(WxLoginReqVO req) {
        // Step 1: 调用微信 jscode2session 换取 openid
        String openid = getOpenidFromWx(req.getCode());

        // Step 2: 以 openid 查询用户
        MallUser user = mallUserMapper.selectOne(
                new LambdaQueryWrapper<MallUser>()
                        .eq(MallUser::getOpenid, openid)
                        .eq(MallUser::getDelFlag, 0)
        );

        // Step 3: 若不存在则注册新用户
        if (user == null) {
            user = new MallUser();
            user.setOpenid(openid);
            user.setNickname(req.getNickname());
            user.setAvatar(req.getAvatar());
            user.setStatus(0);
            mallUserMapper.insert(user);
            log.info("微信新用户注册成功, openid={}, userId={}", openid, user.getId());
        } else {
            // Step 4: 封禁校验
            if (Integer.valueOf(1).equals(user.getStatus())) {
                throw BusinessException.accountBanned();
            }
        }

        // Step 5 & 6: 签发 JWT，构建响应
        String token = generateJwt(user);
        return WxLoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    // ===== H5 登录 =====

    @Override
    public WxLoginVO login(LoginReqVO req) {
        // Step 1: 以 phone 查询用户
        MallUser user = mallUserMapper.selectOne(
                new LambdaQueryWrapper<MallUser>()
                        .eq(MallUser::getPhone, req.getPhone())
                        .eq(MallUser::getDelFlag, 0)
        );

        if (user == null || user.getPassword() == null) {
            throw BusinessException.loginFail();
        }

        // Step 2: BCrypt 校验密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.loginFail();
        }

        // Step 3: 封禁校验
        if (Integer.valueOf(1).equals(user.getStatus())) {
            throw BusinessException.accountBanned();
        }

        // Step 4: 签发 JWT，构建响应
        String token = generateJwt(user);
        return WxLoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .build();
    }

    // ===== 私有方法 =====

    /**
     * 调用微信 jscode2session 接口换取 openid
     */
    @SuppressWarnings("unchecked")
    private String getOpenidFromWx(String code) {
        String url = wxTokenUrl
                + "?appid=" + wxAppid
                + "&secret=" + wxSecret
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw BusinessException.wxAuthFail();
            }

            // 微信返回 errcode 时说明授权失败
            if (body.containsKey("errcode")) {
                int errcode = (int) body.get("errcode");
                String errmsg = (String) body.getOrDefault("errmsg", "unknown");
                log.warn("微信 jscode2session 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw BusinessException.wxAuthFail();
            }

            String openid = (String) body.get("openid");
            if (openid == null || openid.isEmpty()) {
                throw BusinessException.wxAuthFail();
            }
            return openid;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 jscode2session 接口异常", e);
            throw BusinessException.wxAuthFail();
        }
    }

    /**
     * 签发 C端 JWT
     * payload: sub=userId, type=c_end, nickname, exp=7天
     */
    private String generateJwt(MallUser user) {
        byte[] keyBytes = appJwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        // 密钥长度已在启动时由 validateSecret() 确保 >= 32 字节
        Key key = Keys.hmacShaKeyFor(keyBytes);

        long nowMs = System.currentTimeMillis();
        long expMs = nowMs + appJwtConfig.getExpiration() * 1000L;

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "c_end");
        claims.put("nickname", user.getNickname());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(user.getId()))
                .setIssuedAt(new Date(nowMs))
                .setExpiration(new Date(expMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

}
