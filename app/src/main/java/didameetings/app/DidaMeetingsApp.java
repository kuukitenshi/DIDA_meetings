package didameetings.app;

public class DidaMeetingsApp {

    private static final String ASCII_ART = """
            ▄ ▄▖▄ ▄▖  ▖  ▖▄▖▄▖▄▖▄▖▖ ▖▄▖▄▖  ▄▖▄▖▄▖
            ▌▌▐ ▌▌▌▌  ▛▖▞▌▙▖▙▖▐ ▐ ▛▖▌▌ ▚   ▌▌▙▌▙▌
            ▙▘▟▖▙▘▛▌  ▌▝ ▌▙▖▙▖▐ ▟▖▌▝▌▙▌▄▌  ▛▌▌ ▌

                    """;

    private final AppCommunicator communicator;
    private final AppOptions options;

    public DidaMeetingsApp(CliArgs args) {
        this.communicator = new AppCommunicator(args);
        this.options = args.options().copy();
    }

    public void run() {
        System.out.println(ASCII_ART);
        if (this.options.interactiveMode) {
            new InteractiveHandler(this.communicator, this.options).run();
        } else {
            new LoopHandler(this.communicator, this.options).run();
        }
    }

    public void shutdown() {
        this.communicator.shutdown();
    }

    public static void main(String[] args) {
        CliArgs cliArgs = CliArgs.parse(args);
        if (cliArgs == null) {
            System.exit(1);
        }
        DidaMeetingsApp app = new DidaMeetingsApp(cliArgs);
        app.run();
        app.shutdown();
        System.out.println("Goodbye");
    }
}
