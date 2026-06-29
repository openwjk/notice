package com.jkoi.notice.config;

import com.jkoi.notice.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证拦截器：校验 API 请求携带的 Token 是否有效。
 * Token 有效则刷新时效，无效则返回 401。
 * <p>
 * 放行规则：
 * - 非 /api/ 路径（静态资源、SPA 路由等）
 * - 认证接口（/api/auth/**）
 * - 健康检查接口（/api/health）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = resolvePath(request);

        // 非 API 路径一律放行（静态资源、SPA 路由等由前端 Router 处理）
        if (!path.startsWith("/api/")) {
            return true;
        }

        // 放行认证接口
        if (path.startsWith("/api/auth/")) {
            return true;
        }

        // 校验 Token
        String token = extractToken(request);
        if (StringUtils.hasText(token) && authService.validateAndRefreshToken(token)) {
            return true;
        }

        log.warn("Unauthorized request: {}", request.getRequestURI());
        writeUnauthorizedResponse(response);
        return false;
    }

    private String resolvePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private String extractToken(HttpServletRequest request) {
        // 优先从 Authorization Header 获取
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        // 兼容直接传 Token Header
        String token = request.getHeader("X-Token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        return "";
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"未登录或登录已过期\"}");
    }
}
