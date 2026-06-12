package com.jkoi.notice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SystemLogEntry {

    private long sequence;
    private String timestamp;
    private String level;
    private String logger;
    private String thread;
    private String message;
    private String throwable;
}
