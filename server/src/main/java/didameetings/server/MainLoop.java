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
            RequestRecord request = this.state.getRequestHistory().getFirstPending();
            int ballot = this.state.getCurrentBallot();
            int currentLeader = scheduler.leader(ballot);

            if (ballot > -1 && request != null && currentLeader == this.state.getServerId()) {
                LOGGER.info("server {} is leader for ballot {}, processing instance {}, trying value {}",
                        this.state.getServerId(), ballot, instanceId, request.getId());
                
        
                List<Integer> proposedValues = new ArrayList<>();
                proposedValues.add(request.getId());
                boolean ballotAborted = false;
                // change of leader based on ballots
                int previousLeader = this.state.getCompletedBallot() > -1 ? scheduler.leader(this.state.getCompletedBallot()) : -1;
                boolean shouldRunPhaseTwo = paxosInstance.shouldRunPhaseTwo(ballot, currentLeader, previousLeader);

                // PHASE 1
                PhaseOneProcessor phaseOneProcessor;
                if (proposedValues.size() > 1) {
                    phaseOneProcessor = runPhaseOneMultiPaxos(instanceId, ballot, proposedValues);
                }
                if (!phaseOneProcessor.getAccepted()) {
                    ballotAborted = true;
                    int maxballot = phaseOneProcessor.getMaxballot();
                    if (maxballot > this.state.getCurrentBallot()) {
                        this.state.setCurrentBallot(maxballot);
                    }
                } else if (phaseOneProcessor.getValballot() > -1) {
                    proposedValues.set(0, phaseOneProcessor.getValue());
                }
                LOGGER.info("phaseone results: aborted={} value={}, currballot={}, shouldRunPhaseTwo={}", 
                    ballotAborted, proposedValues.get(0), this.state.getCurrentBallot(), shouldRunPhaseTwo);

                // PHASE 2
                if (!ballotAborted) {
                    if (shouldRunPhaseTwo) {
                        LOGGER.info("executing Phase 2 (leader change or first time)");
                        PhaseTwoResponseProcessor phaseTwoProcessor;
                        if (proposedValues.size() > 1) {
                            phaseTwoProcessor = runPhaseTwoMultiPaxos(instanceId, ballot, proposedValues);
                        }
                        LOGGER.info("phasetwo results: aborted={} maxballot={}", !phaseTwoProcessor.getAccepted(),
                                phaseTwoProcessor.getMaxballot());
                        
                        if (!phaseTwoProcessor.getAccepted()) {
                            ballotAborted = true;
                            this.state.setCurrentBallot(phaseTwoProcessor.getMaxballot());
                        } else {
                            paxosInstance.markPhaseTwoExecuted();
                            this.state.setCompletedBallot(ballot);
                            paxosInstance.setCommandId(proposedValues.get(0));
                            paxosInstance.decided = true;
                            LOGGER.info("DECIDED instance {} with reqid {} (Multi-Paxos Phase 2)", instanceId, proposedValues.get(0));
                        }
                    } else {
                        LOGGER.info("no leader change detected, deciding directly (Multi-Paxos optimization)");
                        this.state.setCompletedBallot(ballot);
                        paxosInstance.setCommandId(proposedValues.get(0));
                        paxosInstance.decided = true;
                        LOGGER.info("DECIDED instance {} with reqid {} (Multi-Paxos direct)", instanceId, proposedValues.get(0));
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
            } catch (InterruptedException e) { }
            request = this.state.getRequestHistory().getIfPending(paxosInstance.commandId);
        }

        boolean result = processRequest(request);
        request.setResponse(result);
        this.state.getRequestHistory().moveToProcessed(request.getId());

        StringBuilder sb = new StringBuilder("processed {0} (");
        DidaMeetingsCommand command = request.getCommand();
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
        LOGGER.info(sb.toString(), request.getCommand().action(),
                request.getCommand().meetingId(), request.getCommand().participantId(), request.getCommand().topicId(),
                result);
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

    // Métodos Multi-Paxos que funcionam com o protobuf atual
    private PhaseOneProcessor runPhaseOneMultiPaxos(int instanceId, int ballot, List<Integer> values) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int numAcceptors = acceptors.size();
        int quorum = this.state.getScheduler().quorum(ballot);
        
        // Criar request com lista de valores (quando protobuf compilar)
        PhaseOneRequest.Builder requestBuilder = PhaseOneRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot);
        
        // Adicionar todos os valores à lista
        for (Integer value : values) {
            requestBuilder.addValues(value);
        }
        PhaseOneRequest phaseOneRequest = requestBuilder.build();

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

    private PhaseTwoResponseProcessor runPhaseTwoMultiPaxos(int instanceId, int ballot, List<Integer> values) {
        List<Integer> acceptors = this.state.getScheduler().acceptors(ballot);
        int numAcceptors = acceptors.size();
        int quorum = this.state.getScheduler().quorum(ballot);
        
        // Para agora, usar apenas o primeiro valor (compatibilidade)
        PhaseTwoRequest request = PhaseTwoRequest.newBuilder()
                .setInstance(instanceId)
                .setRequestballot(ballot)
                .setValue(values.get(0))  // PhaseTwoRequest ainda usa value simples
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

    private synchronized void waitForWork() {
        while (this.hasWork == false) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }
}
