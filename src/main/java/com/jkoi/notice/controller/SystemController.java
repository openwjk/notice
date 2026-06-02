package com.jkoi.notice.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author junkai.wang
 * @date 2026/6/2 13:57
 * @description desc
 */
@RestController
@RequestMapping
public class SystemController {
    @RequestMapping("/")
    public String index() {
        return "success";
    }
}
