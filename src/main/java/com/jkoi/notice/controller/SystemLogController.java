package com.jkoi.notice.controller;

import com.jkoi.notice.logging.SystemLogFileService;
import com.jkoi.notice.model.SystemLogEntry;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/system/logs")
public class SystemLogController extends BaseController {

    private final SystemLogFileService systemLogFileService;

    public SystemLogController(SystemLogFileService systemLogFileService) {
        this.systemLogFileService = systemLogFileService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(value = "after", defaultValue = "0") long after,
                                    @RequestParam(value = "before", defaultValue = "0") long before,
                                    @RequestParam(value = "limit", defaultValue = "120") int limit,
                                    @RequestParam(value = "level", required = false) String level,
                                    @RequestParam(value = "start", required = false) String start,
                                    @RequestParam(value = "end", required = false) String end) {
        LocalDateTime startTime = parseTime(start);
        LocalDateTime endTime = parseTime(end);
        SystemLogFileService.LogQueryResult queryResult = systemLogFileService.query(
                after, before, limit, normalizeLevel(level), startTime, endTime);
        List<SystemLogEntry> entries = queryResult.getEntries();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", entries);
        data.put("cursor", queryResult.getCursor());
        data.put("total", queryResult.getTotal());
        data.put("serverTime", LocalDateTime.now().withNano(0).toString());
        data.put("start", startTime == null ? "" : startTime.toString());
        data.put("end", endTime == null ? "" : endTime.toString());
        data.put("source", "file");
        data.put("logFile", queryResult.getLogFile());
        return ok(data);
    }

    private String normalizeLevel(String level) {
        if (!StringUtils.hasText(level) || "ALL".equalsIgnoreCase(level)) {
            return "";
        }
        return level.trim().toUpperCase();
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(" ", "T");
        try {
            if (normalized.length() == 10) {
                return LocalDateTime.parse(normalized + "T00:00:00");
            }
            if (normalized.length() == 16) {
                return LocalDateTime.parse(normalized + ":00");
            }
            return LocalDateTime.parse(normalized);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid log time: " + value);
        }
    }
}
