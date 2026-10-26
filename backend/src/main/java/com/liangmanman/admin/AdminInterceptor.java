package com.liangmanman.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.auth.TokenService;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import com.liangmanman.entity.Admin;
import com.liangmanman.mapper.AdminMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/** 运营后台鉴权：/api/admin/**（登录接口除外）必须带管理员 token，且账号未停用 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final AdminMapper adminMapper;
    private final ObjectMapper objectMapper;

    public AdminInterceptor(TokenService tokenService, AdminMapper adminMapper, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.adminMapper = adminMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().equals("/api/admin/login")) {
            return true;
        }
        String header = request.getHeader("Authorization");
        Long id = header != null && header.startsWith("Bearer ") ? tokenService.parseAdmin(header.substring(7)) : null;
        Admin admin = id == null ? null : adminMapper.selectById(id);
        if (admin == null || !Boolean.TRUE.equals(admin.getEnabled())) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResult.fail(BizException.UNAUTHORIZED, "请重新登录")));
            return false;
        }
        AdminContext.set(admin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AdminContext.clear();
    }
}
