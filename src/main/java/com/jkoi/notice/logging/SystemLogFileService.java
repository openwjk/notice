package com.jkoi.notice.logging;

import com.jkoi.notice.model.SystemLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SystemLogFileService {

    private static final Pattern BOOT_LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?)\\s+([A-Z]+)\\s+\\d+\\s+---\\s+\\[([^\\]]*)]\\s+(.+?)\\s+:\\s?(.*)$"
    );
    private static final DateTimeFormatter LOG_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    private final Path logFilePath;
    private final Path rollingLogDirectory;
    private final String rollingLogGlob;

    public SystemLogFileService(@Value("${logging.file.name:logs/notice.log}") String logFileName,
                                @Value("${logging.logback.rollingpolicy.file-name-pattern:logs/notice.%d{yyyy-MM-dd}.log}") String rollingFilePattern) {
        this.logFilePath = Paths.get(logFileName).toAbsolutePath().normalize();
        Path rollingSamplePath = Paths.get(rollingFilePattern.replace("%d{yyyy-MM-dd}", "2000-01-01"))
                .toAbsolutePath()
                .normalize();
        this.rollingLogDirectory = rollingSamplePath.getParent();
        this.rollingLogGlob = rollingSamplePath.getFileName().toString().replace("2000-01-01", "*");
    }

    public LogQueryResult query(long after, long before, int limit, String level, LocalDateTime start, LocalDateTime end) {
        List<SystemLogEntry> allEntries = readEntries();
        int safeLimit = Math.max(1, Math.min(limit, 300));
        boolean previousPageQuery = before > 0;
        List<SystemLogEntry> matched = new ArrayList<SystemLogEntry>();
        for (SystemLogEntry entry : allEntries) {
            if (!previousPageQuery && after > 0 && entry.getSequence() <= after) {
                continue;
            }
            if (previousPageQuery && entry.getSequence() >= before) {
                continue;
            }
            if (StringUtils.hasText(level) && !level.equalsIgnoreCase(entry.getLevel())) {
                continue;
            }
            LocalDateTime entryTime = parseEntryTime(entry.getTimestamp());
            if (start != null && entryTime != null && entryTime.isBefore(start)) {
                continue;
            }
            if (end != null && entryTime != null && entryTime.isAfter(end)) {
                continue;
            }
            matched.add(entry);
        }

        List<SystemLogEntry> entries;
        boolean rangeQuery = start != null || end != null;
        if (previousPageQuery && matched.size() > safeLimit) {
            entries = new ArrayList<SystemLogEntry>(matched.subList(matched.size() - safeLimit, matched.size()));
        } else if (after <= 0 && rangeQuery && matched.size() > safeLimit) {
            entries = new ArrayList<SystemLogEntry>(matched.subList(0, safeLimit));
        } else if (after <= 0 && matched.size() > safeLimit) {
            entries = new ArrayList<SystemLogEntry>(matched.subList(matched.size() - safeLimit, matched.size()));
        } else if (matched.size() > safeLimit) {
            entries = new ArrayList<SystemLogEntry>(matched.subList(0, safeLimit));
        } else {
            entries = matched;
        }

        long cursor = entries.isEmpty() ? latestSequence(allEntries) : entries.get(entries.size() - 1).getSequence();
        return new LogQueryResult(entries, cursor, allEntries.size(), logFilePath.toString());
    }

    private List<SystemLogEntry> readEntries() {
        List<SystemLogEntry> entries = new ArrayList<SystemLogEntry>();
        for (Path logFile : resolveLogFiles()) {
            readEntries(logFile, entries);
        }
        renumber(entries);
        return entries;
    }

    private List<Path> resolveLogFiles() {
        List<Path> logFiles = new ArrayList<Path>();
        if (rollingLogDirectory != null && Files.isDirectory(rollingLogDirectory)) {
            try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(rollingLogDirectory, rollingLogGlob)) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        logFiles.add(path.toAbsolutePath().normalize());
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (Files.isRegularFile(logFilePath) && !logFiles.contains(logFilePath)) {
            logFiles.add(logFilePath);
        }
        Collections.sort(logFiles, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                try {
                    return Files.getLastModifiedTime(left).compareTo(Files.getLastModifiedTime(right));
                } catch (IOException ignored) {
                    return left.toString().compareTo(right.toString());
                }
            }
        });
        return logFiles;
    }

    private void readEntries(Path logFile, List<SystemLogEntry> entries) {
        SystemLogEntry current = null;
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = BOOT_LOG_PATTERN.matcher(line);
                if (matcher.matches()) {
                    current = toEntry(0L, matcher);
                    entries.add(current);
                    continue;
                }
                appendContinuation(current, line);
            }
        } catch (IOException ignored) {
        }
    }

    private void renumber(List<SystemLogEntry> entries) {
        Collections.sort(entries, new Comparator<SystemLogEntry>() {
            @Override
            public int compare(SystemLogEntry left, SystemLogEntry right) {
                LocalDateTime leftTime = parseEntryTime(left.getTimestamp());
                LocalDateTime rightTime = parseEntryTime(right.getTimestamp());
                if (leftTime == null && rightTime == null) {
                    return 0;
                }
                if (leftTime == null) {
                    return 1;
                }
                if (rightTime == null) {
                    return -1;
                }
                return leftTime.compareTo(rightTime);
            }
        });
        for (int index = 0; index < entries.size(); index++) {
            SystemLogEntry entry = entries.get(index);
            long sequence = toStableSequence(parseEntryTime(entry.getTimestamp()), index + 1L);
            if (index > 0 && sequence <= entries.get(index - 1).getSequence()) {
                sequence = entries.get(index - 1).getSequence() + 1L;
            }
            entry.setSequence(sequence);
        }
    }

    private long latestSequence(List<SystemLogEntry> entries) {
        return entries.isEmpty() ? 0L : entries.get(entries.size() - 1).getSequence();
    }

    private long toStableSequence(LocalDateTime time, long fallback) {
        if (time == null) {
            return fallback;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1000L;
    }

    private SystemLogEntry toEntry(long sequence, Matcher matcher) {
        SystemLogEntry entry = new SystemLogEntry();
        entry.setSequence(sequence);
        entry.setTimestamp(formatTimestamp(matcher.group(1)));
        entry.setLevel(matcher.group(2));
        entry.setThread(matcher.group(3).trim());
        entry.setLogger(shortLogger(matcher.group(4).trim()));
        entry.setMessage(matcher.group(5));
        entry.setThrowable("");
        return entry;
    }

    private void appendContinuation(SystemLogEntry current, String line) {
        if (current == null) {
            return;
        }
        String existing = current.getThrowable();
        if (StringUtils.hasText(existing)) {
            current.setThrowable(existing + "\n" + line);
        } else {
            current.setThrowable(line);
        }
    }

    private String formatTimestamp(String value) {
        LocalDateTime parsed = parseLogTime(value);
        return parsed == null ? value.replace(" ", "T") : parsed.withNano(0).toString();
    }

    private LocalDateTime parseEntryTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ex) {
            return parseLogTime(value.replace("T", " "));
        }
    }

    private LocalDateTime parseLogTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), LOG_TIME_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private String shortLogger(String loggerName) {
        if (!StringUtils.hasText(loggerName)) {
            return "";
        }
        int index = loggerName.lastIndexOf('.');
        return index >= 0 ? loggerName.substring(index + 1) : loggerName;
    }

    public static class LogQueryResult {

        private final List<SystemLogEntry> entries;
        private final long cursor;
        private final int total;
        private final String logFile;

        public LogQueryResult(List<SystemLogEntry> entries, long cursor, int total, String logFile) {
            this.entries = entries;
            this.cursor = cursor;
            this.total = total;
            this.logFile = logFile;
        }

        public List<SystemLogEntry> getEntries() {
            return entries;
        }

        public long getCursor() {
            return cursor;
        }

        public int getTotal() {
            return total;
        }

        public String getLogFile() {
            return logFile;
        }
    }
}
