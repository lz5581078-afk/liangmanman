package com.liangmanman.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liangmanman.common.ApiResult;
import com.liangmanman.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 解析 Authorization: Bearer <token>。
 * 公开接口（逛店、搜索、看商品）不要求登录，但带了 token 也会识别用户；其他接口必须登录。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 只对 GET 公开的路径 */
    private static final List<String> PUBLIC_GET = List.of(
            "/api/stores/**",
            "/api/products/**",
            "/api/search",
            "/api/categories",
            "/api/standard-products",
            "/api/group-buys/**",
            "/api/skus/**",
            "/api/banners",
            "/api/qrcode",
            "/api/app-config");

    /** 任何方法都公开的路径 */
    private static final List<String> PUBLIC_ANY = List.of(
            "/api/auth/**",
            "/api/pay/notify/**");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(TokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            userId = tokenService.parse(header.substring(7));
        }
        if (userId != null) {
            UserContext.set(userId);
            return true;
        }
        String path = request.getRequestURI();
        if (matches(PUBLIC_ANY, path) || ("GET".equalsIgnoreCase(request.getMethod()) && matches(PUBLIC_GET, path))) {
            return true;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.fail(BizException.UNAUTHORIZED, "请先登录")));
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean matches(List<String> patterns, String path) {
        for (String p : patterns) {
            if (matcher.match(p, path)) {
                return true;
            }
        }
        return false;
    }
}
