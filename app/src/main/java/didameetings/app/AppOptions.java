package didameetings.app;

import java.util.Arrays;
import java.util.List;

public class AppOptions {

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final List<String> VALID_ARGUMENTS = Arrays.asList(
            "--help", "-h",
            "--no-interactive",
            "--mrange",
            "--prange",
            "--trange",
            "--length",
            "--sleep");

    public int meetingRange = 1000;
    public int participantRange = 1000;
    public int topicRange = 1000;
    public int loopLength = 20;
    public int sleepRange = 6;
    public boolean interactiveMode = true;
    public boolean showHelp = false;

    public static AppOptions parse(String[] args) {
        AppOptions options = new AppOptions();
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (!option.startsWith("-")) {
                continue;
            }
            if (!VALID_ARGUMENTS.contains(option)) {
                System.err.println("Unknown argument: " + option);
                return null;
            }
            if (option.equals("--help") || option.equals("-h")) {
                options.showHelp = true;
            } else if (option.equals("--no-interactive")) {
                options.interactiveMode = false;
            } else if (i == args.length - 1) {
                System.err.println("No value supplied for argument " + option);
                return null;
            } else {
                int value = 0;
                try {
                    value = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.out.printf("Value for argument %s needs to be a number!%n", option);
                    return null;
                }
                switch (option) {
                    case "--mrange" -> options.meetingRange = value;
                    case "--prange" -> options.participantRange = value;
                    case "--trange" -> options.topicRange = value;
                    case "--length" -> options.loopLength = value;
                    case "--sleep" -> options.sleepRange = value;
                    default -> {
                        return null;
                    }
                }
                i++;
            }
        }
        return options;
    }

    public AppOptions copy() {
        AppOptions copy = new AppOptions();
        copy.meetingRange = this.meetingRange;
        copy.participantRange = this.participantRange;
        copy.topicRange = this.topicRange;
        copy.loopLength = this.loopLength;
        copy.sleepRange = this.sleepRange;
        copy.interactiveMode = this.interactiveMode;
        copy.showHelp = this.showHelp;
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AppOptions {").append(LINE_SEPARATOR);
        sb.append("\tmrange = ").append(this.meetingRange).append(LINE_SEPARATOR);
        sb.append("\tprange = ").append(this.participantRange).append(LINE_SEPARATOR);
        sb.append("\ttrange = ").append(this.topicRange).append(LINE_SEPARATOR);
        sb.append("\tloop = ").append(this.loopLength).append(LINE_SEPARATOR);
        sb.append("\tsleep = ").append(this.sleepRange).append(LINE_SEPARATOR);
        sb.append("\tinteractive = ").append(this.interactiveMode).append(LINE_SEPARATOR);
        return sb.append("}").toString();
    }
}
