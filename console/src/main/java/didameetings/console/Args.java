package didameetings.console;

import didameetings.configs.ConfigurationScheduler;

public record Args(String host, int port, ConfigurationScheduler scheduler) {

    public static Args parse(String[] args) {
        if (args.length != 3) {
            System.err.println("Invalid number of arguments!\nRequired arguments: <host> <port> <scheduler>");
            return null;
        }
        String host = args[0];
        int port = 0;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port!");
            return null;
        }
        ConfigurationScheduler scheduler = new ConfigurationScheduler();
        if (args[2].equals("B")) {
            scheduler.setSchedule('B');
        } else if (!args[2].equals("A")) {
            System.err.println("Invalid scheduler, available schedulers: A, B");
            return null;
        }
        return new Args(host, port, scheduler);
    }
}
