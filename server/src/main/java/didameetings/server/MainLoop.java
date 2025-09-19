package didameetings.server;

import java.util.ArrayList;
import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;
import didameetings.DidaMeetingsPaxos.PhaseOneRequest;
import didameetings.DidaMeetingsPaxos.PhaseTwoReply;
import didameetings.DidaMeetingsPaxos.PhaseTwoRequest;
import didameetings.configs.ConfigurationScheduler;
import didameetings.core.MeetingManager;
import didameetings.util.CollectorStreamObserver;
import didameetings.util.DebugPrinter;
import didameetings.util.GenericResponseCollector;
import didameetings.util.PhaseOneProcessor;
import didameetings.util.PhaseTwoResponseProcessor;

public class MainLoop implements Runnable {

    private final DidaMeetingsServerState state;
    private boolean hasWork = false;
    private int nextLogEntry = -1;

    public MainLoop(DidaMeetingsServerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        waitForWork();
        while (true) {
            this.nextLogEntry++;
            this.processEntry(this.nextLogEntry);
        }
    }

    public synchronized void wakeup() {
        this.hasWork = true;
        notify();
    }

    public synchronized void processEntry(int instanceId) {
        ConfigurationScheduler scheduler = this.state.getScheduler();
        PaxosInstance paxosInstance = this.state.getPaxosLog().testAndSetEntry(instanceId);

        while (!paxosInstance.decided) {
            boolean retryImmediately = false;
            RequestRecord request = this.state.getRequestHistory().getFirstPending();
            int ballot = this.state.getCurrentBallot();

            if (ballot > -1 && request != null && scheduler.leader(ballot) == this.state.getServerId()) {
                DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Server " + this.state.getServerId() + " is leader for ballot " + ballot + ", processing instance " + instanceId);
                DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Current ballot=" + this.state.getCurrentBallot() + ", completed ballot=" + this.state.getCompletedBallot());
                boolean ballotAborted = false;
                int phaseTwoValue = request.getId();
                
                // Check if this instance is already decided
                if (paxosInstance.decided) {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Instance " + instanceId + " is already decided with value " + paxosInstance.commandId);
                    break;
                }

                // Phase 1
                DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Starting Phase 1 (Prepare) for instance " + instanceId + " with ballot " + ballot);
                PhaseOneProcessor phaseOneProcessor = runPhaseOne(instanceId, ballot);
                if (!phaseOneProcessor.getAccepted()) {
                    ballotAborted = true;
                    retryImmediately = true;
                    int maxballot = phaseOneProcessor.getMaxballot();
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Phase 1 FAILED - ballot aborted due to higher ballot " + maxballot);
                    if (maxballot > this.state.getCurrentBallot()) {
                        this.state.setCurrentBallot(maxballot);
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Updated current ballot to " + maxballot);
                    }
                } else {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Phase 1 SUCCESS - got " + phaseOneProcessor.getPromises() + " promises");
                    if (phaseOneProcessor.getValballot() > -1) {
                        phaseTwoValue = phaseOneProcessor.getValue();
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Using previously accepted value " + phaseTwoValue + " from valballot " + phaseOneProcessor.getValballot());
                    } else {
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: No previously accepted value, using new value " + phaseTwoValue);
                    }
                }

                // Phase 2
                if (!ballotAborted) {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Starting Phase 2 (Accept) for instance " + instanceId + " with ballot " + ballot + " and value " + phaseTwoValue);
                    PhaseTwoResponseProcessor phaseTwoProcessor = runPhaseTwo(instanceId, ballot, phaseTwoValue);
                    if (!phaseTwoProcessor.getAccepted()) {
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Phase 2 FAILED - ballot aborted due to higher ballot " + phaseTwoProcessor.getMaxballot());
                        retryImmediately = true;
                        this.state.setCurrentBallot(phaseTwoProcessor.getMaxballot());
                    } else {
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Phase 2 SUCCESS - got " + phaseTwoProcessor.getAccepts() + " accepts");
                        this.state.setCompletedBallot(ballot);
                        paxosInstance.commandId = phaseTwoValue;
                        paxosInstance.decided = true;
                        DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: DECIDED instance " + instanceId + " with value " + phaseTwoValue + " for ballot " + ballot);
                    }
                } else {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Skipping Phase 2 due to ballot abort");
                }
            } else {
                // Not the leader for this ballot or no ballot/request
                if (ballot > -1 && request != null) {
                    int currentLeader = scheduler.leader(ballot);
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Server " + this.state.getServerId() + " is NOT leader for ballot " + ballot + " (leader is " + currentLeader + ")");
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Giving up leadership - not leader for current ballot");
                } else if (ballot == -1) {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: No ballot assigned yet, waiting for work");
                } else if (request == null) {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: No pending requests, waiting for work");
                }
            }
            if (!paxosInstance.decided) {
                if (!retryImmediately) {
                    this.hasWork = false;
                    waitForWork();
                } else {
                    DebugPrinter.debugPrint("[PAXOS-DEBUG] MainLoop: Retrying instance " + instanceId + " immediately after aborted ballot");
                }
            }
        }

        RequestRecord request = this.state.getRequestHistory().getIfPending(paxosInstance.commandId);
        while (request == null) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
            request = this.state.getRequestHistory().getIfPending(paxosInstance.commandId);
        }

        boolean result = processRequest(request);
        System.out.println("[MainLoop] Executed " + request.getCommand().action() +
                "(mid=" + request.getCommand().meetingId() + ", pid=" + request.getCommand().participantId() +
                ", topic=" + request.getCommand().topicId() + ") => result=" + result);
        request.setResponse(result);
        this.state.getRequestHistory().moveToProcessed(request.getId());
    }

    private PhaseOneProcessor runPhaseOne(int instanceId, int ballot) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int numAcceptors = acceptors.size();
        int quorum = this.state.getScheduler().quorum(ballot);
        PhaseOneRequest phaseOneRequest = PhaseOneRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot)
                .build();

        PhaseOneProcessor phaseOneProcessor = new PhaseOneProcessor(quorum);
        List<PhaseOneReply> phaseOneResponses = new ArrayList<>();
        GenericResponseCollector<PhaseOneReply> phaseOneCollector = new GenericResponseCollector<>(
                phaseOneResponses, numAcceptors, phaseOneProcessor);
        for (int i = 0; i < numAcceptors; i++) {
            int acceptorId = acceptors.get(i);
            CollectorStreamObserver<PhaseOneReply> observer = new CollectorStreamObserver<>(phaseOneCollector);
            this.state.getPaxosStub(acceptorId).phaseone(phaseOneRequest, observer);
        }
        phaseOneCollector.waitUntilDone();
        return phaseOneProcessor;
    }

    private PhaseTwoResponseProcessor runPhaseTwo(int instanceId, int ballot, int value) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int numAcceptors = acceptors.size();
        int quorum = this.state.getScheduler().quorum(ballot);
        PhaseTwoRequest request = PhaseTwoRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot)
                .setValue(value)
                .build();

        PhaseTwoResponseProcessor processor = new PhaseTwoResponseProcessor(quorum);
        List<PhaseTwoReply> responses = new ArrayList<>();
        GenericResponseCollector<PhaseTwoReply> collector = new GenericResponseCollector<>(
                responses, numAcceptors, processor);
        for (int i = 0; i < numAcceptors; i++) {
            int acceptorId = acceptors.get(i);
            CollectorStreamObserver<PhaseTwoReply> observer = new CollectorStreamObserver<>(
                    collector);
            this.state.getPaxosStub(acceptorId).phasetwo(request, observer);
        }
        collector.waitUntilDone();
        return processor;
    }

    private boolean processRequest(RequestRecord request) {
        DidaMeetingsCommand command = request.getCommand();
        MeetingManager meetingManager = this.state.getMeetingManager();
        DidaMeetingsAction action = command.action();
        return switch (action) {
            case OPEN -> meetingManager.open(command.meetingId(), 10);
            case ADD -> meetingManager.addAndClose(command.meetingId(), command.participantId());
            case TOPIC -> meetingManager.setTopic(command.meetingId(), command.participantId(), command.topicId());
            case CLOSE -> meetingManager.close(command.meetingId());
            case DUMP -> {
                meetingManager.dump();
                yield true;
            }
            default -> false;
        };
    }

    private synchronized void waitForWork() {
        while (this.hasWork == false) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }
}
