package com.jkoi.notice.controller;

import com.jkoi.notice.service.AuthService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 请求发送验证码（通过企微机器人推送）
     */
    @PostMapping("/code")
    public Map<String, Object> sendCode() {
        boolean sent = authService.sendVerificationCode();
        if (sent) {
            return ok("验证码已发送，请查看企微机器人消息");
        }
        return fail("验证码发送失败，请稍后重试");
    }

    /**
     * 使用验证码登录，验证成功后返回 Token
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        if (!StringUtils.hasText(code)) {
            return fail("请输入验证码");
        }

        String token = authService.verifyAndIssueToken(code);
        if (token == null) {
            return fail("验证码无效或已过期");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        return ok(data);
    }

    /**
     * 注销登录
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        authService.revokeToken(token);
        return ok("已注销");
    }
}
