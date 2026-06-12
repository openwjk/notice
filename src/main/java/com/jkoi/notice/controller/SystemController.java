package com.jkoi.notice.controller;

import com.jkoi.notice.service.WechatIdentityService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping
public class SystemController extends BaseController {

    private final WechatIdentityService wechatIdentityService;

    public SystemController(WechatIdentityService wechatIdentityService) {
        this.wechatIdentityService = wechatIdentityService;
    }

    @RequestMapping("/")
    public Object index(@RequestParam(value = "code", required = false) String code) {
        if (StringUtils.hasText(code)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("wxid", wechatIdentityService.resolveWxId(code));
            return ok(data);
        }
        return "success";
    }
}
