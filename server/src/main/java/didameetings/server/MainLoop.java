package didameetings.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;
import didameetings.DidaMeetingsPaxos.PhaseOneRequest;
import didameetings.DidaMeetingsPaxos.PhaseTwoReply;
import didameetings.DidaMeetingsPaxos.PhaseTwoRequest;
import didameetings.DidaMeetingsPaxos.WrittenValue;
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

    private Map<Integer, Integer> previousRequests = new HashMap<>();
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
        while (!this.hasWork) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        this.hasWork = false;
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
                this.previousRequests.clear();
                PhaseOneProcessor processor = runPhaseOne(this.instanceToPropose, ballot);
                LOGGER.debug("phaseone results: aborted={} maxballot={}", !processor.getAccepted(),
                        processor.getMaxballot());
                if (!processor.getAccepted()) {
                    int maxballot = processor.getMaxballot();
                    if (maxballot > this.state.getCurrentBallot()) {
                        this.state.setCurrentBallot(maxballot);
                    }
                    break;
                } else {
                    int maxInstance = this.instanceToPropose;
                    for (Map.Entry<Integer, WrittenValue> entry : processor.getWrittenValues().entrySet()) {
                        int instance = entry.getKey();
                        WrittenValue written = entry.getValue();
                        int reqid = written.getValue();
                        this.reservedRequests.add(written.getValue());
                        this.previousRequests.put(instance, reqid);
                        if (instance > maxInstance) {
                            maxInstance = instance;
                        }
                    }

                    for (int i = this.instanceToPropose; i < maxInstance; i++) {
                        this.previousRequests.putIfAbsent(i, -2);
                    }
                }
            }

            int valueToPropose = this.previousRequests.getOrDefault(this.instanceToPropose, -1);
            if (valueToPropose == -1) {
                RequestRecord request = findNextPendingRequest();
                if (request == null) {
                    break;
                }
                valueToPropose = request.getId();
            }
            this.reservedRequests.add(valueToPropose);
            dispatchPhaseTwo(paxosInstance.instanceId, ballot, valueToPropose);
            this.instanceToPropose++;
        }
    }

    private synchronized void tryProcessEntry() {
        while (true) { // processa todas as instâncias decididas disponíveis (caso chegue mais de uma
                       // just in case)
            PaxosInstance paxosInstance = this.state.getPaxosLog().testAndSetEntry(this.instanceToProcess);
            if (!paxosInstance.decided) {
                break;
            }
            // NOP
            if (paxosInstance.commandId == -2) {
                this.instanceToProcess++;
                LOGGER.info("processed NOP");
                continue;
            }
            RequestRecord request = this.state.getRequestHistory().getIfPending(paxosInstance.commandId);
            if (request == null) {
                break;
            }
            boolean result = processRequest(request);
            request.setResponse(result);
            this.state.getRequestHistory().moveToProcessed(request.getId());
            this.reservedRequests.remove(request.getId());
            this.instanceToProcess++;
        }
        // verify if can process TOPIC requests after processing normal ones
        processTopicQueue();
    }

    private synchronized void processTopicQueue() {
        RequestHistory requestHistory = this.state.getRequestHistory();
        MeetingManager meetingManager = this.state.getMeetingManager();

        List<RequestRecord> postponedRequests = new ArrayList<>();
        int originalQueueSize = requestHistory.getTopicQueueSize();
        int processed = 0;
        
        while (!requestHistory.isTopicQueueEmpty() && processed < originalQueueSize) {
            RequestRecord topicRequest = requestHistory.pollFromTopicQueue();
            if (topicRequest == null) {
                break;
            }
            processed++;
            
            DidaMeetingsCommand command = topicRequest.getCommand();
            int meetingId = command.meetingId();
            int participantId = command.participantId();
            boolean canExecuteTopic = false;
            
            List<Integer> participantsWithoutTopic = meetingManager.participantsWithoutTopic(meetingId);
            if (participantsWithoutTopic != null && participantsWithoutTopic.contains(participantId)) {
                canExecuteTopic = true;
            }
            if (canExecuteTopic) {
                boolean result = processRequest(topicRequest);
                topicRequest.setResponse(result);
                requestHistory.moveToProcessed(topicRequest.getId());
                LOGGER.info("> TOPIC request processed from queue: mid={}, pid={}, topic={}, result={}", 
                           meetingId, participantId, command.topicId(), result);
            } else {
                postponedRequests.add(topicRequest);
                LOGGER.debug("> TOPIC request postponed: meeting {} doesn't exist or participant {} not in meeting", meetingId, participantId);
            }
        }
        // requeue postponed requests
        for (RequestRecord postponedRequest : postponedRequests) {
            requestHistory.addToTopicQueue(postponedRequest);
        }
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
                LOGGER.info("DECIDED instace {} with value {}", instanceId, value == -2 ? "NOP" : value);
            }
            wakeup();
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
