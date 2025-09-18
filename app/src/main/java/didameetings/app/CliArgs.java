package didameetings.app;

import java.util.Arrays;

import didameetings.configs.ConfigurationScheduler;

public record CliArgs(int clientId, String host, int port, ConfigurationScheduler scheduler, AppOptions options) {

    public static CliArgs parse(String[] args) {
        if (args.length < 4) {
            System.err.println("Invalid number of arguments.\nUsage: <id> <host> <post> <scheduler> [OPTIONS]");
            return null;
        }
        int clientId = 0;
        try {
            clientId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Id needs to be a number!");
            return null;
        }
        if (clientId < 1 || clientId > 99) {
            System.err.println("Id needs to be in [1, 99] range");
            return null;
        }
        String host = args[1];
        int port = 0;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Port needs to be a number!");
            return null;
        }
        String schedulerType = args[3];
        if (!schedulerType.equals("A") && !schedulerType.equals("B")) {
            System.err.println("Invalid scheduler, available: A, B");
            return null;
        }
        ConfigurationScheduler scheduler = new ConfigurationScheduler(schedulerType.charAt(0));
        AppOptions options = new AppOptions();
        if (args.length >= 4) {
            String[] optionalArgs = Arrays.copyOfRange(args, 4, args.length);
            options = AppOptions.parse(optionalArgs);
        }
        return new CliArgs(clientId, host, port, scheduler, options);
    }
}
