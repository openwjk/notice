package com.jkoi.notice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class ScheduledFactory {

    @Autowired
    private List<ScheduledService> scheduledServiceList;

    private Map<String, ScheduledService> scheduledServiceMap;

    @PostConstruct
    private void init() {
        this.scheduledServiceMap = new HashMap<>();
        for (ScheduledService scheduledService : scheduledServiceList) {
            String exeCode = scheduledService.getCode();
            if (Objects.nonNull(exeCode)) {
                scheduledServiceMap.put(exeCode, scheduledService);
            }
        }
    }

    public ScheduledService getScheduledService(String exeCode) {
        ScheduledService service = scheduledServiceMap.get(exeCode);
        if (service == null) {
            log.warn("ScheduledFactory.getScheduledService: null, origin code: {}", exeCode);
        }
        return service;
    }

    public List<Map<String, Object>> listExecutionCodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ScheduledService> entry : scheduledServiceMap.entrySet()) {
            result.add(buildExecutionCode(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public String getExecutionCodeSample(String code) {
        ScheduledService scheduledService = scheduledServiceMap.get(code);
        return scheduledService == null ? "{}" : scheduledService.getSample();
    }

    private Map<String, Object> buildExecutionCode(String code, ScheduledService service) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", service.getName());
        item.put("title", service.getName());
        item.put("sample", service.getSample());
        return item;
    }
}
