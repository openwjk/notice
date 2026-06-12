package com.jkoi.notice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class WxIdInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WxIdInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isRootRequest(request)) {
            return true;
        }

        String wxid = request.getHeader("wxid");
        String xWxId = request.getHeader("X-Wx-Id");
        if (StringUtils.hasText(wxid) || StringUtils.hasText(xWxId)) {
            return true;
        }

        // 支持通过环境变量跳过 wxid 校验
        String ignoreWxId = System.getenv("JKOI_NOTICE_IGNORE_WXID");
        if ("true".equalsIgnoreCase(ignoreWxId)) {
            return true;
        }

        log.warn("Missing wxid header, request: {}", request.getRequestURI());
        writeEmptyResponse(response);
        return false;
    }

    private boolean isRootRequest(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        String path = StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        return "/".equals(path) || path.isEmpty();
    }

    private void writeEmptyResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{}");
    }
}
