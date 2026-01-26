package es.buni.hcb.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() {}

    public static void info(String message) {
        log("INFO", message, null);
    }

    public static void warn(String message) {
        log("WARN", message, null);
    }

    public static void error(String message) {
        log("ERROR", message, null);
    }

    public static void error(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }

    public static void critical(String message) {
        critical(message, null);
    }

    public static void critical(String message, Throwable throwable) {
        log("CRITICAL", message, throwable);
        System.exit(1);
    }

    private static void log(String level, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("[%s] [%s] %s", timestamp, level, message);

        if ("ERROR".equals(level) || "CRITICAL".equals(level)) {
            System.err.println(logMessage);
        } else {
            System.out.println(logMessage);
        }

        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            System.err.println(sw);
        }
    }
}
