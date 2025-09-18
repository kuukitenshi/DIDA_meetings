package didameetings.server;

import didameetings.DidaMeetingsPaxosServiceGrpc;
import didameetings.DidaMeetingsPaxosServiceGrpc.DidaMeetingsPaxosServiceStub;
import didameetings.configs.ConfigurationScheduler;
import didameetings.core.MeetingManager;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class DidaMeetingsServerState {

    private final PaxosLog paxosLog = new PaxosLog();
    private final RequestHistory requestHistory = new RequestHistory();
    private final MeetingManager meetingManager = new MeetingManager();
    private final int serverId;
    private final ConfigurationScheduler scheduler;
    private final DidaMeetingsPaxosServiceStub[] paxosStubs;

    private int currentBallot = 0;
    private int completedBallot = -1;

    // Debug state
    private boolean isFrozen = false;
    private boolean isSlowMode = false;

    public DidaMeetingsServerState(CliArgs args) {
        this.serverId = args.serverId();
        this.scheduler = args.scheduler();
        int nodeCount = this.scheduler.allparticipants().size();
        this.paxosStubs = new DidaMeetingsPaxosServiceStub[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            int port = args.basePort() + i;
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
            this.paxosStubs[i] = DidaMeetingsPaxosServiceGrpc.newStub(channel);
        }
    }

    public PaxosLog getPaxosLog() {
        return this.paxosLog;
    }

    public RequestHistory getRequestHistory() {
        return this.requestHistory;
    }

    public MeetingManager getMeetingManager() {
        return meetingManager;
    }

    public int getServerId() {
        return this.serverId;
    }

    public ConfigurationScheduler getScheduler() {
        return this.scheduler;
    }

    public DidaMeetingsPaxosServiceStub getPaxosStub(int serverId) {
        return this.paxosStubs[serverId];
    }

    public synchronized void setDebugMode(int mode) {
        System.out.println("[Debug] setDebugMode(" + mode + ")");
        switch (mode) {
            case 1: // crash
                System.out.println("[Debug] Crashing server " + this.serverId);
                System.exit(1);
                break;
            case 2: // freeze
                System.out.println("[Debug] Freezing server " + this.serverId);
                this.isFrozen = true;
                break;
            case 3: // un-freeze
                System.out.println("[Debug] Un-freezing server " + this.serverId);
                this.isFrozen = false;
                this.notifyAll();
                break;
            case 4: // slow-mode-on
                System.out.println("[Debug] Slow-mode ON for server " + this.serverId);
                this.isSlowMode = true;
                break;
            case 5: // slow-mode-off
                System.out.println("[Debug] Slow-mode OFF for server " + this.serverId);
                this.isSlowMode = false;
                break;
            default:
                System.out.println("[Debug] Unknown mode: " + mode);
                break;
        }
    }

    public synchronized void waitIfFrozen() {
        while (this.isFrozen) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public void randomDelay() {
        if (!this.isSlowMode) {
            return;
        }
        try {
            // 50ms to 200ms delay
            long delay = 50 + (long) (Math.random() * 150);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
        }
    }

    public synchronized int getCurrentBallot() {
        return this.currentBallot;
    }

    public synchronized void setCurrentBallot(int ballot) {
        if (ballot > this.currentBallot) {
            this.currentBallot = ballot;
        }
    }

    public synchronized int getCompletedBallot() {
        return this.completedBallot;
    }

    public int findMaxDecidedBallot() {
        int ballot = -1;
        for (PaxosInstance entry : this.paxosLog.entries()) {
            if (entry.decided && entry.acceptBallot > ballot) {
                ballot = entry.acceptBallot;
            }
        }
        return ballot;
    }

    public synchronized void updateCompletedBallot(int ballot) {
        // WARNING: THIS ONLY WORKS FOR CONFIGURATIONS WHERE THERE IS NO NEED FOR
        // STATE-TRANSFER!!!!!
        // NEEDS TO BE UPDATE FOR THE PROJECT TODO:

        ballot = this.findMaxDecidedBallot();
        if (ballot > this.completedBallot)
            this.completedBallot = ballot;
        this.notifyAll();
    }

    public synchronized void setCompletedBallot(int ballot) {
        if (ballot > this.completedBallot) {
            this.completedBallot = ballot;
        }
        this.notifyAll();
    }

    public synchronized int waitForCompletedBallot(int ballot) {
        while (this.completedBallot < ballot) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return this.completedBallot;
    }
}
