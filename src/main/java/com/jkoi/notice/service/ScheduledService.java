package com.jkoi.notice.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Date;


public interface ScheduledService {
    String getCode();

    String getName();

    String getSample();

    void execute(Date date, JsonNode node);
}
