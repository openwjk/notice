package com.jkoi.notice.controller;

import com.jkoi.notice.service.WechatIdentityService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author junkai.wang
 * @date 2026/6/2 13:57
 * @description desc
 */
@RestController
@RequestMapping
public class SystemController {

    private final WechatIdentityService wechatIdentityService;

    public SystemController(WechatIdentityService wechatIdentityService) {
        this.wechatIdentityService = wechatIdentityService;
    }

    @RequestMapping("/")
    public Object index(@RequestParam(value = "code", required = false) String code) {
        if (code != null) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("wxid", wechatIdentityService.resolveWxId(code));
            return ok(data);
        }
        return "success";
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }
}
