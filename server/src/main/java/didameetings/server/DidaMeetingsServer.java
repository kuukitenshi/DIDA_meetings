package didameetings.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class DidaMeetingsServer {

    private static final String ASCII_ART = """
            ▄ ▄▖▄ ▄▖  ▖  ▖▄▖▄▖▄▖▄▖▖ ▖▄▖▄▖  ▄▖▄▖▄▖▖▖▄▖▄▖
            ▌▌▐ ▌▌▌▌  ▛▖▞▌▙▖▙▖▐ ▐ ▛▖▌▌ ▚   ▚ ▙▖▙▘▌▌▙▖▙▘
            ▙▘▟▖▙▘▛▌  ▌▝ ▌▙▖▙▖▐ ▟▖▌▝▌▙▌▄▌  ▄▌▙▖▌▌▚▘▙▖▌▌

            """;

    public static void main(String[] args) throws Exception {
        CliArgs cliArgs = CliArgs.parse(args);
        if (cliArgs == null) {
            return;
        }

        DidaMeetingsServerState state = new DidaMeetingsServerState(cliArgs);
        MainLoop mainLoop = new MainLoop(state);
        new Thread(mainLoop).start();
        BindableService mainService = new DidaMeetingsMainServiceImpl(state, mainLoop);
        BindableService masterService = new DidaMeetingsMasterServiceImpl(state, mainLoop);
        BindableService paxosService = new DidaMeetingsPaxosServiceImpl(state, mainLoop);

        int port = cliArgs.basePort() + cliArgs.serverId();
        Server server = ServerBuilder.forPort(port)
                .addService(mainService)
                .addService(masterService)
                .addService(paxosService).build();
        server.start();
        System.out.println(ASCII_ART);
        System.out.println("Server started on port " + port);
        server.awaitTermination();
    }
}
