package didameetings.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class FancyLogger implements Logger {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String name;

    public FancyLogger(String name) {
        this.name = name;
    }

    public void log(LogLevel level, String format, Object... args) {
        Date now = new Date();
        String formattedDate = DATE_FORMAT.format(now);
        String message = parseFormat(format, args);
        String log = String.format("[%s] (%s) %s > %s", formattedDate, level, this.name, message);
        String color = switch (level) {
            case DEBUG -> ANSIColors.MAGENTA_FG;
            case INFO -> ANSIColors.GREEN_FG;
            case WARNING -> ANSIColors.YELLOW_FG;
            case ERROR -> ANSIColors.RED_FG;
            default -> ANSIColors.RESET;
        };
        System.out.println(color + log + ANSIColors.RESET);
    }

    private String parseFormat(String format, Object... args) {
        String message = format.replaceAll("\\{\\}", "%s");
        for (int i = 0; i < args.length; i++) {
            message = message.replaceAll("\\{" + i + "\\}", args[i].toString());
        }
        return String.format(message, args);
    }
}
