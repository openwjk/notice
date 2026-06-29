package com.jkoi.notice.config;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 前端路由支持：当浏览器直接访问前端路由路径（如 /list, /editor）时，
 * 转发到 index.html，由前端 Router 处理。
 * 仅对非 API、非静态资源路径生效。
 */
@Controller
public class SpaRedirectConfig implements ErrorController {

    @RequestMapping("/error")
    public String handleError() {
        return "forward:/index.html";
    }
}
