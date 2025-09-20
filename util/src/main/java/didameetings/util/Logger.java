package didameetings.util;

public interface Logger {

    void log(LogLevel level, String format, Object... args);

    default void trace(String message) {
        log(LogLevel.TRACE, message);
    }

    default void trace(String format, Object... args) {
        log(LogLevel.TRACE, format, args);
    }

    default void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    default void debug(String format, Object... args) {
        log(LogLevel.DEBUG, format, args);
    }

    default void info(String message) {
        log(LogLevel.INFO, message);
    }

    default void info(String format, Object... args) {
        log(LogLevel.INFO, format, args);
    }

    default void warn(String message) {
        log(LogLevel.WARNING, message);
    }

    default void warn(String format, Object... args) {
        log(LogLevel.WARNING, format, args);
    }

    default void error(String message) {
        log(LogLevel.ERROR, message);
    }

    default void error(String format, Object... args) {
        log(LogLevel.ERROR, format, args);
    }
}
