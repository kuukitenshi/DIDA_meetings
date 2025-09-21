package didameetings.server;

import didameetings.DidaMeetingsPaxosServiceGrpc;
import didameetings.DidaMeetingsPaxosServiceGrpc.DidaMeetingsPaxosServiceStub;
import didameetings.configs.ConfigurationScheduler;
import didameetings.core.MeetingManager;
import didameetings.util.FancyLogger;
import didameetings.util.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class DidaMeetingsServerState {

    private static final Logger LOGGER = new FancyLogger("State");
    private static final int MIN_SLOW_DELAY = 2000;
    private static final long MAX_SLOW_DELAY = 10000;

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
            String target = "localhost:" + port;
            ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
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
        setDebugMode(mode, -1, -1);
    }

    public synchronized void setDebugMode(int mode, int instance, int value) {
        LOGGER.debug("activated debug mode {} with instance={} value={}", mode, instance, value);
        switch (mode) {
            case 1: // crash
                LOGGER.debug("crasing the server...");
                System.exit(1);
                break;
            case 2: // freeze
                LOGGER.debug("freeze ENABLED");
                this.isFrozen = true;
                break;
            case 3: // un-freeze
                LOGGER.debug("freeze DISABLED");
                this.isFrozen = false;
                notifyAll();
                break;
            case 4: // slow-mode-on
                LOGGER.debug("slow mode ENABLED");
                this.isSlowMode = true;
                break;
            case 5: // slow-mode-off
                LOGGER.debug("slow mode DISABLED");
                this.isSlowMode = false;
                break;
            default:
                LOGGER.warn("received unknown debug mode '{}'", mode);
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
            long delay = MIN_SLOW_DELAY + (long) (Math.random() * MAX_SLOW_DELAY);
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

    public synchronized boolean setInstanceValue(int instanceId, int value) {
        try {
            PaxosInstance instance = this.paxosLog.testAndSetEntry(instanceId);
            instance.commandId = value;
            LOGGER.debug("Debug: Set instance {} value to {}", instanceId, value);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to set instance {} value to {}: {}", instanceId, value, e.getMessage());
            return false;
        }
    }

    public synchronized boolean setInstanceValue(int replica, int instanceId, String value) {
        try {
            int intValue = Integer.parseInt(value);
            PaxosInstance instance = this.paxosLog.testAndSetEntry(instanceId);
            instance.commandId = intValue;
            instance.writeTimestamp = java.time.Instant.now();
            LOGGER.debug("WriteValue: Set replica {} instance {} value to {}", replica, instanceId, value);
            return true;
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse value '{}' as integer for instance {}", value, instanceId);
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to set instance {} value to {}: {}", instanceId, value, e.getMessage());
            return false;
        }
    }

    public synchronized boolean setInstanceValue(int replica, int instanceId, int value) {
        try {
            PaxosInstance instance = this.paxosLog.testAndSetEntry(instanceId);
            instance.commandId = value;
            instance.writeTimestamp = java.time.Instant.now(); 
            LOGGER.debug("WriteValue: Set replica {} instance {} value to {}", replica, instanceId, value);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to set instance {} value to {}: {}", instanceId, value, e.getMessage());
            return false;
        }
    }

    public synchronized boolean setInstanceValue(int replica, int instanceId, int value, int ballot) {
        try {
            PaxosInstance instance = this.paxosLog.testAndSetEntry(instanceId);
            instance.commandId = value;
            instance.writeBallot = ballot;
            instance.writeTimestamp = java.time.Instant.now();
            LOGGER.debug("WriteValue: Set replica {} instance {} value to {} with ballot {}", 
                    replica, instanceId, value, ballot);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to set instance {} value to {} with ballot {}: {}", instanceId, value, ballot, e.getMessage());
            return false;
        }
    }
}
