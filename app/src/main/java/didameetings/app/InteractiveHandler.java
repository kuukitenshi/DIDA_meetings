package didameetings.app;

import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;

import didameetings.DidaMeetingsMain.AddReply;
import didameetings.DidaMeetingsMain.CloseReply;
import didameetings.DidaMeetingsMain.DumpReply;
import didameetings.DidaMeetingsMain.OpenReply;
import didameetings.DidaMeetingsMain.TopicReply;

public class InteractiveHandler {

    private final AppCommunicator communicator;
    private final AppOptions options;

    public InteractiveHandler(AppCommunicator communicator, AppOptions options) {
        this.communicator = communicator;
        this.options = options;
    }

    public void run() {
        helpCommand();
        boolean shouldQuit = false;
        try (Scanner sc = new Scanner(System.in)) {
            while (!shouldQuit) {
                System.out.print("~> ");
                String input = sc.nextLine();
                String[] split = input.split(" ");
                String command = split[0].toLowerCase();
                String[] commandArgs = Arrays.copyOfRange(split, 1, split.length);
                switch (command) {
                    case "help" -> helpCommand();
                    case "open" -> openCommand(commandArgs);
                    case "close" -> closeCommand(commandArgs);
                    case "topic" -> topicCommand(commandArgs);
                    case "add" -> addCommand(commandArgs);
                    case "show" -> showCommand();
                    case "length" -> lengthCommand(commandArgs);
                    case "mrange" -> mrangeCommand(commandArgs);
                    case "prange" -> prangeCommand(commandArgs);
                    case "time" -> timeCommand(commandArgs);
                    case "loop" -> {
                        shouldQuit = true;
                        new LoopHandler(this.communicator, this.options).run();
                    }
                    case "exit" -> shouldQuit = true;
                    default -> System.err.println("Invalid command, type help for the list of commands.");
                }
            }
        }
    }

    private void helpCommand() {
        System.out.println("+----------[ HELP ]----------+");
        System.out.println("> help - display help menu.");
        System.out.println("> open <meeting id> - opens a new meetings.");
        System.out.println("> close <meeting id> - closes a meeting.");
        System.out.println(
                "> topic <meeting id> <participant id> <topic> - assigns a topic to a participant in a meetings.");
        System.out.println("> add <mid> <pid> - adds a participant to a meeting.");
        System.out.println("> show - displays information in the replicas.");
        System.out.println("> length <length> - ...");
        System.out.println("> mrange <> - ...");
        System.out.println("> prange <> - ...");
        System.out.println("> time <> - ...");
        System.out.println("> loop - stops interactive mode and goes to loop.");
        System.out.println("> exit - quits the app.\n");
    }

    private void openCommand(String[] args) {
        if (args.length != 1) {
            System.err.println("You need to specify the meeting id!");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Meeting id needs to be a number!");
            return;
        }
        Optional<OpenReply> replyOpt = this.communicator.open(mid);
        replyOpt.ifPresentOrElse(reply -> {
            if (reply.getResult()) {
                System.out.printf("Meeting with id %s has been opened.%n", mid);
            } else {
                System.out.printf("Couldn't open meeting with id %s%n", mid);
            }
        }, () -> System.out.println("ERROR: No reply received!"));
    }

    private void closeCommand(String[] args) {
        if (args.length != 1) {
            System.err.println("Wrong number of arguments!");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Meeting id needs to be a number!");
            return;
        }
        Optional<CloseReply> replyOpt = this.communicator.close(mid);
        replyOpt.ifPresentOrElse(reply -> {
            if (reply.getResult()) {
                System.out.printf("Meeting with id %s has been closed.%n", mid);
            } else {
                System.out.printf("Couldn't close meeting with id %s%n", mid);
            }
        }, () -> System.out.println("ERROR: No reply received!"));
    }

    private void topicCommand(String[] args) {
        if (args.length != 3) {
            System.err.println("Wrong number of arguments!");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Meeting id needs to be a number!");
            return;
        }
        int pid;
        try {
            pid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Participant id needs to be a number!");
            return;
        }
        int topic;
        try {
            topic = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Topic needs to be a number!");
            return;
        }
        Optional<TopicReply> replyOpt = this.communicator.topic(mid, pid, topic);
        replyOpt.ifPresentOrElse(reply -> {
            if (reply.getResult()) {
                System.out.printf("Participant %s has been assigned topic %s in meeting %s%n.", pid, topic, mid);
            } else {
                System.out.printf("Couldn't assign participant %s the topic %s in meeting %s%n.", pid, topic, mid);
            }
        }, () -> System.out.println("ERROR: No reply received!"));
    }

    private void addCommand(String[] args) {
        if (args.length != 2) {
            System.err.println("Wrong number of arguments!");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Meeting id needs to be a number!");
            return;
        }
        int pid;
        try {
            pid = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Participant id needs to be a number!");
            return;
        }
        Optional<AddReply> replyOpt = this.communicator.add(mid, pid);
        replyOpt.ifPresentOrElse(reply -> {
            if (reply.getResult()) {
                System.out.printf("Participant %s has been added to meeting %s%n.", pid, mid);
            } else {
                System.out.printf("Couldn't add participant %s to meeting %s%n.", pid, mid);
            }
        }, () -> System.out.println("ERROR: No reply received!"));
    }

    private void showCommand() {
        Optional<DumpReply> replyOpt = this.communicator.show();
        replyOpt.ifPresentOrElse(reply -> {
            if (reply.getResult()) {
                System.out.println("Information has been dumped on the replicas.");
            } else {
                System.out.println("Couldn't dump information about replicas.");
            }
        }, () -> System.out.println("ERROR: No reply received!"));
    }

    private void lengthCommand(String[] args) {
        optionValueAssigner(args, (value) -> {
            this.options.loopLength = value;
        });
    }

    private void mrangeCommand(String[] args) {
        optionValueAssigner(args, (value) -> {
            this.options.meetingRange = value;
        });
    }

    private void prangeCommand(String[] args) {
        optionValueAssigner(args, (value) -> {
            this.options.participantRange = value;
        });

    }

    private void timeCommand(String[] args) {
        optionValueAssigner(args, (value) -> {
            this.options.sleepRange = value;
        });
    }

    private void optionValueAssigner(String[] args, Consumer<Integer> consumer) {
        if (args.length != 1) {
            System.err.println("Wrong number of arguments!");
            return;
        }
        int value = 0;
        try {
            value = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Value needs to be a number!");
            return;
        }
        consumer.accept(value);
    }
}
