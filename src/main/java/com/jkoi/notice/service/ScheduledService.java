package com.jkoi.notice.service;

import java.util.Date;


public interface ScheduledService {
    String getCode();

    void execute(Date date);
}
