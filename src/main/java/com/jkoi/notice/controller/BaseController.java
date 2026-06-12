package com.jkoi.notice.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制器基类，提供统一的响应包装方法
 */
public abstract class BaseController {

    protected Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    protected Map<String, Object> fail(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
