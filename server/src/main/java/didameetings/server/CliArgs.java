package didameetings.server;

import didameetings.configs.ConfigurationScheduler;

public record CliArgs(int basePort, int serverId, ConfigurationScheduler scheduler, int maxParticipants) {

    public static CliArgs parse(String[] args) {
        if (args.length != 4) {
            System.err
                    .println("Invalid number of arguments!\nRequired: <base port> <id> <scheduler> <max participants>");
            return null;
        }

        int basePort = 0;
        try {
            basePort = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Base port needs to be a number");
            return null;
        }

        int serverId = 0;
        try {
            serverId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Id needs to be a number!");
            return null;
        }

        String schedulerType = args[2];
        if (!schedulerType.equals("A") && !schedulerType.equals("B")) {
            System.err.println("Invalid scheduler, use either A or B");
            return null;
        }
        ConfigurationScheduler scheduler = new ConfigurationScheduler(schedulerType.charAt(0));

        int maxParticipants = 0;
        try {
            maxParticipants = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Max participants needs to be a number!");
            return null;
        }

        return new CliArgs(basePort, serverId, scheduler, maxParticipants);
    }
}
