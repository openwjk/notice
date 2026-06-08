package com.jkoi.notice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author wangjunkai
 * @description
 * @date 2023/7/28 13:11
 */
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
        ScheduledService indexCodeService = scheduledServiceMap.get(exeCode);
        if (indexCodeService != null) {
            return indexCodeService;
        } else {
            log.warn("ScheduledFactory.getScheduledService:null,origin code:{}", exeCode);
        }
        return null;
    }
}
