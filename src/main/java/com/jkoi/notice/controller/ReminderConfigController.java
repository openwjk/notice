package com.jkoi.notice.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jkoi.notice.model.ReminderConfig;
import com.jkoi.notice.service.ReminderConfigService;
import com.jkoi.notice.service.ScheduledFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/reminders")
public class ReminderConfigController {

    private final ReminderConfigService reminderConfigService;
    private final ScheduledFactory scheduledFactory;

    public ReminderConfigController(ReminderConfigService reminderConfigService,
                                    ScheduledFactory scheduledFactory) {
        this.reminderConfigService = reminderConfigService;
        this.scheduledFactory = scheduledFactory;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        return ok(reminderConfigService.getDashboard());
    }

    @GetMapping("/export")
    public ArrayNode export() {
        return reminderConfigService.exportSchedulerPayload();
    }

    @GetMapping("/exe-codes")
    public Map<String, Object> executionCodes() {
        return ok(scheduledFactory.listExecutionCodes());
    }

    @GetMapping("/exe-codes/{code}/sample")
    public Map<String, Object> executionCodeSample(@PathVariable String code) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("code", code);
        data.put("sample", scheduledFactory.getExecutionCodeSample(code));
        return ok(data);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody ReminderConfig reminderConfig) {
        ReminderConfig saved = reminderConfigService.save(reminderConfig);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("item", saved);
        data.put("dashboard", reminderConfigService.getDashboard());
        return ok(data);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
                                      @RequestBody ReminderConfig reminderConfig) {
        reminderConfig.setId(id);
        return save(reminderConfig);
    }

    @PutMapping("/{id}/toggle")
    public Map<String, Object> toggleEnabled(@PathVariable String id) {
        ReminderConfig toggled = reminderConfigService.toggleEnabled(id);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("item", toggled);
        data.put("dashboard", reminderConfigService.getDashboard());
        return ok(data);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        boolean deleted = reminderConfigService.delete(id);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("deleted", deleted);
        data.put("dashboard", reminderConfigService.getDashboard());
        return ok(data);
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody ReminderConfig reminderConfig) {
        return ok(reminderConfigService.testSend(reminderConfig));
    }

    @PostMapping("/cron/preview")
    public Map<String, Object> cronPreview(@RequestBody ReminderConfig reminderConfig) {
        return ok(reminderConfigService.previewCron(reminderConfig));
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }
}
