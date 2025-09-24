package didameetings.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

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
    private final ExecutorService phaseTwoExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Integer> reservedRequests = new HashSet<>();

    private boolean hasWork = false;
    private int instanceToPropose = 0;
    private int instanceToProcess = 0;
    private int lastBallotAsLeader = -1;

    public MainLoop(DidaMeetingsServerState state) {
        this.state = state;
    }

    @Override
    public void run() {
        while (true) {
            waitForWork();
            tryProposeEntry();
            tryProcessEntry();
        }
    }

    private synchronized void waitForWork() {
        this.hasWork = false;
        while (!this.hasWork) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public synchronized void wakeup() {
        this.hasWork = true;
        notifyAll();
    }

    public synchronized void tryProposeEntry() {
        while (true) {
            ConfigurationScheduler scheduler = this.state.getScheduler();
            PaxosInstance paxosInstance = this.state.getPaxosLog().testAndSetEntry(this.instanceToPropose);
            while (paxosInstance.decided) {
                this.instanceToPropose++;
                paxosInstance = this.state.getPaxosLog().testAndSetEntry(this.instanceToPropose);
            }
            int ballot = this.state.getCurrentBallot();
            if (ballot <= -1 || this.state.getServerId() != scheduler.leader(ballot)) {
                break;
            }

            if (ballot > lastBallotAsLeader) {
                LOGGER.info("server {} is leader for ballot {}, running phase one once", this.state.getServerId(),
                        ballot);
                lastBallotAsLeader = ballot;
                this.reservedRequests.clear();
                PhaseOneProcessor processor = runPhaseOne(this.instanceToPropose, ballot);
                LOGGER.debug("phaseone results: aborted={} maxballot={}", !processor.getAccepted(),
                        processor.getMaxballot());
                if (!processor.getAccepted()) {
                    int maxballot = processor.getMaxballot();
                    if (maxballot > this.state.getCurrentBallot()) {
                        this.state.setCurrentBallot(ballot);
                    }
                    break;
                } else {
                    processor.getWrittenValues().forEach((instance, written) -> {
                        PaxosInstance paxos = this.state.getPaxosLog().testAndSetEntry(instance);
                        paxos.commandId = written.getValue();
                        paxos.writeBallot = written.getBallot();
                        this.reservedRequests.add(paxos.commandId);
                    });
                }
            }

            int phaseTwoValue = paxosInstance.commandId;
            if (phaseTwoValue == -1) {
                RequestRecord request = findNextPendingRequest();
                if (request == null) {
                    break;
                }
                phaseTwoValue = request.getId();
            }
            this.reservedRequests.add(phaseTwoValue);
            dispatchPhaseTwo(paxosInstance.instanceId, ballot, phaseTwoValue);
            this.instanceToPropose++;
        }
    }

    private synchronized void tryProcessEntry() {
        PaxosInstance paxosInstance = this.state.getPaxosLog().testAndSetEntry(this.instanceToProcess);
        if (!paxosInstance.decided) {
            return;
        }
        RequestRecord request = this.state.getRequestHistory().getIfPending(paxosInstance.commandId);
        if (request == null) {
            return;
        }
        boolean result = processRequest(request);
        request.setResponse(result);
        this.state.getRequestHistory().moveToProcessed(request.getId());
        this.reservedRequests.remove(request.getId());
        this.instanceToProcess++;
    }

    private RequestRecord findNextPendingRequest() {
        Optional<RequestRecord> opt = this.state.getRequestHistory().getAllPending()
                .stream()
                .filter(r -> !this.reservedRequests.contains(r.getId()))
                .findFirst();
        return opt.isPresent() ? opt.get() : null;
    }

    private PhaseOneProcessor runPhaseOne(int instanceId, int ballot) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int quorum = this.state.getScheduler().quorum(ballot);
        PhaseOneRequest request = PhaseOneRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot)
                .build();

        PhaseOneProcessor processor = new PhaseOneProcessor(quorum);
        List<PhaseOneReply> responses = new ArrayList<>();
        GenericResponseCollector<PhaseOneReply> collector = new GenericResponseCollector<>(responses, acceptors.size(),
                processor);
        for (int acceptor : acceptors) {
            CollectorStreamObserver<PhaseOneReply> observer = new CollectorStreamObserver<>(collector);
            this.state.getPaxosStub(acceptor).phaseone(request, observer);
        }
        collector.waitUntilDone();
        return processor;
    }

    private PhaseTwoResponseProcessor runPhaseTwo(int instanceId, int ballot, int value) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int quorum = this.state.getScheduler().quorum(ballot);
        PhaseTwoRequest request = PhaseTwoRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot)
                .setValue(value)
                .build();

        PhaseTwoResponseProcessor processor = new PhaseTwoResponseProcessor(quorum);
        List<PhaseTwoReply> responses = new ArrayList<>();
        GenericResponseCollector<PhaseTwoReply> collector = new GenericResponseCollector<>(
                responses, acceptors.size(), processor);
        for (int acceptor : acceptors) {
            CollectorStreamObserver<PhaseTwoReply> observer = new CollectorStreamObserver<>(collector);
            this.state.getPaxosStub(acceptor).phasetwo(request, observer);
        }
        collector.waitUntilDone();
        return processor;
    }

    private void dispatchPhaseTwo(int instanceId, int ballot, int value) {
        this.phaseTwoExecutor.submit(() -> {
            PhaseTwoResponseProcessor processor = runPhaseTwo(instanceId, ballot, value);
            if (!processor.getAccepted()) {
                this.state.setCurrentBallot(processor.getMaxballot());
            } else {
                this.state.setCompletedBallot(ballot);
                LOGGER.info("DECIDED instace {} with value {}", instanceId, value);
            }
        });
    }

    private boolean processRequest(RequestRecord request) {
        DidaMeetingsCommand command = request.getCommand();
        MeetingManager meetingManager = this.state.getMeetingManager();
        DidaMeetingsAction action = command.action();
        boolean result = switch (action) {
            case OPEN -> meetingManager.open(command.meetingId(), this.state.getMaxParticipants());
            case ADD -> meetingManager.addAndClose(command.meetingId(), command.participantId());
            case TOPIC -> meetingManager.setTopic(command.meetingId(), command.participantId(), command.topicId());
            case CLOSE -> meetingManager.close(command.meetingId());
            case DUMP -> {
                meetingManager.dump();
                yield true;
            }
            default -> false;
        };
        logCommand(command, result);
        return result;
    }

    private void logCommand(DidaMeetingsCommand command, boolean result) {
        StringBuilder sb = new StringBuilder("processed {0} (");
        if (command.meetingId() != -1) {
            sb.append("mid={1}");
            if (command.participantId() != -1) {
                sb.append(", pid={2}");
            }
            if (command.topicId() != -1) {
                sb.append(", topic={3}");
            }
        }
        sb.append(") => result={4}");
        LOGGER.info(sb.toString(), command.action(), command.meetingId(), command.participantId(), command.topicId(),
                result);
    }
}
