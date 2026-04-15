package io.github.driftn2forty.chatsentry.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DebugLogger {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Logger logger;
    private final Path logDirectory;
    private final boolean logToConsole;
    private final boolean logToFile;
    private final boolean debugEnabled;
    private LocalDate currentDate;
    private Path currentLogFile;

    public DebugLogger(Logger logger, Path logDirectory, boolean logToConsole, boolean logToFile, boolean debugEnabled) {
        this.logger = logger;
        this.logDirectory = logDirectory;
        this.logToConsole = logToConsole;
        this.logToFile = logToFile;
        this.debugEnabled = debugEnabled;
    }

    public void debug(String source, String message) {
        if (!debugEnabled) {
            return;
        }
        log(Level.FINE, "DEBUG", source, message);
    }

    public void info(String source, String message) {
        log(Level.INFO, "INFO", source, message);
    }

    public void warn(String source, String message) {
        log(Level.WARNING, "WARN", source, message);
    }

    public void error(String source, String message) {
        log(Level.SEVERE, "ERROR", source, message);
    }

    public void error(String source, String message, Throwable throwable) {
        log(Level.SEVERE, "ERROR", source, message);
        if (logToConsole) {
            logger.log(Level.SEVERE, message, throwable);
        }
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    private void log(Level level, String levelTag, String source, String message) {
        final String formatted = String.format("[%s] [%s] [%s] %s", LocalDateTime.now().format(TIMESTAMP_FMT), levelTag, source, message);

        if (logToConsole) {
            logger.log(level, String.format("[%s] %s", source, message));
        }

        if (logToFile) {
            writeToFile(formatted);
        }
    }

    private void writeToFile(String line) {
        try {
            final LocalDate today = LocalDate.now();
            if (currentLogFile == null || !today.equals(currentDate)) {
                currentDate = today;
                Files.createDirectories(logDirectory);
                currentLogFile = logDirectory.resolve("chatsentry-" + today + ".log");
            }
            Files.writeString(currentLogFile, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write to log file", e);
        }
    }
}
