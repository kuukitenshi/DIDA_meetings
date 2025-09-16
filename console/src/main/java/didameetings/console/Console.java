package didameetings.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import didameetings.DidaMeetingsMasterServiceGrpc;
import didameetings.DidaMeetingsMaster.NewBallotReply;
import didameetings.DidaMeetingsMaster.NewBallotRequest;
import didameetings.DidaMeetingsMaster.SetDebugReply;
import didameetings.DidaMeetingsMaster.SetDebugRequest;
import didameetings.DidaMeetingsMasterServiceGrpc.DidaMeetingsMasterServiceStub;

import didameetings.util.*;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Console {

    private static final String ASCII_ART = """
            ▄ ▄▖▄ ▄▖  ▖  ▖▄▖▄▖▄▖▄▖▖ ▖▄▖▄▖  ▄▖▄▖▖ ▖▄▖▄▖▖ ▄▖
            ▌▌▐ ▌▌▌▌  ▛▖▞▌▙▖▙▖▐ ▐ ▛▖▌▌ ▚   ▌ ▌▌▛▖▌▚ ▌▌▌ ▙▖
            ▙▘▟▖▙▘▛▌  ▌▝ ▌▙▖▙▖▐ ▟▖▌▝▌▙▌▄▌  ▙▖▙▌▌▝▌▄▌▙▌▙▖▙▖
            """;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ManagedChannel[] channels;
    private final DidaMeetingsMasterServiceStub[] stubs;
    private AtomicInteger completedBallot = new AtomicInteger(-1);
    private int sequenceNumber = 0;

    public Console(Args cliArgs) {
        int nodeCount = cliArgs.scheduler().allparticipants().size();
        this.channels = new ManagedChannel[nodeCount];
        this.stubs = new DidaMeetingsMasterServiceStub[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            String address = String.format("%s:%s", cliArgs.host(), cliArgs.port() + i);
            this.channels[i] = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
            this.stubs[i] = DidaMeetingsMasterServiceGrpc.newStub(this.channels[i]);
        }
    }

    public void run() {
        boolean shouldQuit = false;
        helpCommand();
        try (Scanner sc = new Scanner(System.in)) {
            while (!shouldQuit) {
                System.out.print("~> ");
                String input = sc.nextLine();
                String[] split = input.split(" ");
                String command = split[0].toLowerCase();
                String[] commandArgs = Arrays.copyOfRange(split, 1, split.length);
                switch (command) {
                    case "help":
                        helpCommand();
                        break;
                    case "debug":
                        debugCommand(commandArgs);
                        break;
                    case "ballot":
                        ballotCommand(commandArgs);
                        break;
                    case "exit":
                        shouldQuit = true;
                        break;
                    default:
                        System.err.println("Invalid command! Use help to show commands.");
                        break;
                }
            }
        }
    }

    public void shutdown() {
        this.executor.shutdownNow();
        for (ManagedChannel channel : this.channels) {
            channel.shutdownNow();
        }
    }

    private void helpCommand() {
        System.out.println("+----------[ HELP ]----------+");
        System.out.println("> help - display help menu.");
        System.out.println(
                "> ballot <number> <replica> - starts the ballot with the specified number in the given replica.");
        System.out.println("> debug <mode> <replica> - sets the given replica in the specified debug mode.");
        System.out.println("> exit - quits the console.\n");
    }

    private void debugCommand(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid number of arguments!");
            return;
        }
        int mode = 0;
        try {
            mode = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Debug mode needs to be a number!");
            return;
        }
        int replica = 0;
        try {
            replica = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Replica needs to be a number!");
            return;
        }

        int reqid = nextRequestId();
        SetDebugRequest request = SetDebugRequest.newBuilder()
                .setReqid(reqid)
                .setMode(mode)
                .build();
        List<SetDebugReply> responses = new ArrayList<>();
        GenericResponseCollector<SetDebugReply> collector = new GenericResponseCollector<>(responses, 1);
        CollectorStreamObserver<SetDebugReply> observer = new CollectorStreamObserver<>(collector);
        this.stubs[replica].setdebug(request, observer);
        collector.waitForQuorum(1);
        if (responses.isEmpty()) {
            System.err.println("No reply received for the given replica :(");
            return;
        }
        SetDebugReply reply = responses.getFirst();
        if (reply.getAck()) {
            System.out.println("Debug mode has been set!");
        } else {
            System.out.println("DEbug mode has been denied!");
        }
    }

    private void ballotCommand(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid number of arguments!");
            return;
        }
        int newBallot = 0;
        try {
            newBallot = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Ballot needs to be a number!");
            return;
        }
        int replica = 0;
        try {
            replica = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Replica needs to be a number!");
            return;
        }

        int reqid = nextRequestId();
        NewBallotRequest request = NewBallotRequest.newBuilder()
                .setReqid(reqid)
                .setNewballot(newBallot)
                .setCompletedballot(completedBallot.get())
                .build();
        List<NewBallotReply> responses = new ArrayList<>();
        GenericResponseCollector<NewBallotReply> collector = new GenericResponseCollector<>(responses, 1);
        CollectorStreamObserver<NewBallotReply> observer = new CollectorStreamObserver<>(collector);
        this.stubs[replica].newballot(request, observer);
        System.out.println("Request sent!");

        this.executor.submit(() -> {
            collector.waitForQuorum(1);
            if (responses.isEmpty()) {
                System.err.println("No reply received from the given replica :(");
                return;
            }
            NewBallotReply reply = responses.getFirst();
            int completed = reply.getCompletedballot();
            this.completedBallot.set(completed);
            System.out.println("New completed ballot: " + completed);
        });
    }

    private int nextRequestId() {
        this.sequenceNumber++;
        return this.sequenceNumber * 100;
    }

    public static void main(String[] args) throws Exception {
        Args cliArgs = Args.parse(args);
        if (cliArgs == null) {
            System.exit(1);
        }
        System.out.println(ASCII_ART);
        Console console = new Console(cliArgs);
        console.run();
        console.shutdown();
        System.out.println("Goodbye");
    }
}
