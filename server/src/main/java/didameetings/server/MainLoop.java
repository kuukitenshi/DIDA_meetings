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
import didameetings.util.FancyLogger;
import didameetings.util.GenericResponseCollector;
import didameetings.util.Logger;
import didameetings.util.PhaseOneProcessor;
import didameetings.util.PhaseTwoResponseProcessor;

public class MainLoop implements Runnable {

    private static final Logger LOGGER = new FancyLogger("MainLoop");

    private final DidaMeetingsServerState state;

    private boolean hasWork = false;
    private int nextLogEntry = -1;

    public MainLoop(DidaMeetingsServerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        // waitForWork();
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
            RequestRecord request = this.state.getRequestHistory().getFirstPending();
            int ballot = this.state.getCurrentBallot();

            if (ballot > -1 && request != null && scheduler.leader(ballot) == this.state.getServerId()) {
                LOGGER.info("server {} is leader for ballot {}, processing instance {}", this.state.getServerId(),
                        ballot, instanceId);
                boolean ballotAborted = false;
                int phaseTwoValue = request.getId();

                // Phase 1
                PhaseOneProcessor phaseOneProcessor = runPhaseOne(instanceId, ballot);
                if (!phaseOneProcessor.getAccepted()) {
                    ballotAborted = true;
                    int maxballot = phaseOneProcessor.getMaxballot();
                    if (maxballot > this.state.getCurrentBallot()) {
                        this.state.setCurrentBallot(maxballot);
                    }
                } else if (phaseOneProcessor.getValballot() > -1) {
                    phaseTwoValue = phaseOneProcessor.getValue();
                }

                // Phase 2
                if (!ballotAborted) {
                    PhaseTwoResponseProcessor phaseTwoProcessor = runPhaseTwo(instanceId, ballot, phaseTwoValue);
                    if (!phaseTwoProcessor.getAccepted()) {
                        ballotAborted = true;
                        this.state.setCurrentBallot(phaseTwoProcessor.getMaxballot());
                    } else {
                        this.state.setCompletedBallot(ballot);
                        paxosInstance.commandId = phaseTwoValue;
                        paxosInstance.decided = true;
                        LOGGER.info("decided instace {} with reqid {}", instanceId, phaseTwoValue);
                    }
                }
            }
            if (!paxosInstance.decided) {
                this.hasWork = false;
                waitForWork();
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
        request.setResponse(result);
        this.state.getRequestHistory().moveToProcessed(request.getId());
        LOGGER.info("processed {} (mid={}, pid={}, topic={}) => result={}", request.getCommand().action(),
                request.getCommand().meetingId(), request.getCommand().participantId(), request.getCommand().topicId(),
                result);
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
