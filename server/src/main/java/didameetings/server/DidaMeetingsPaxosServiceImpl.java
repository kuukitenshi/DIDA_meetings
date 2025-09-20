package didameetings.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import didameetings.DidaMeetingsPaxos.LearnReply;
import didameetings.DidaMeetingsPaxos.LearnRequest;
import didameetings.DidaMeetingsPaxos.PhaseOneReply;
import didameetings.DidaMeetingsPaxos.PhaseOneRequest;
import didameetings.DidaMeetingsPaxos.PhaseTwoReply;
import didameetings.DidaMeetingsPaxos.PhaseTwoRequest;
import didameetings.DidaMeetingsPaxosServiceGrpc.DidaMeetingsPaxosServiceImplBase;
import didameetings.util.CollectorStreamObserver;
import didameetings.util.FancyLogger;
import didameetings.util.GenericResponseCollector;
import didameetings.util.Logger;
import io.grpc.stub.StreamObserver;

public class DidaMeetingsPaxosServiceImpl extends DidaMeetingsPaxosServiceImplBase {

    private static final Logger LOGGER = new FancyLogger("PaxosService");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final DidaMeetingsServerState state;
    private final MainLoop mainLoop;

    public DidaMeetingsPaxosServiceImpl(DidaMeetingsServerState state, MainLoop mainLoop) {
        this.state = state;
        this.mainLoop = mainLoop;
    }

    @Override
    public void phaseone(PhaseOneRequest request, StreamObserver<PhaseOneReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int instance = request.getInstance();
        int ballot = request.getRequestballot();
        
        // Multi-Paxos: ler lista de valores propostos
        List<Integer> proposedValues = request.getValuesList();
        LOGGER.debug("received phaseone request (instance={}, ballot={}, proposedValues={})", 
            instance, ballot, proposedValues);

        PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance, ballot);
        boolean accepted = false;
        
        // Para compatibilidade, usar o primeiro valor ou o existente
        int value = entry.getCommandId();  // usar método atualizado
        int valballot = entry.writeBallot;
        if (ballot >= this.state.getCurrentBallot()) {
            accepted = true;
            this.state.setCurrentBallot(ballot);
            entry.readBallot = ballot;
        }
        int maxballot = this.state.getCurrentBallot();
        LOGGER.debug("phaseone results: instance={}, maxballot={}, accepted={}, val={}, valballot={}", instance,
                maxballot, accepted, value, valballot);

        // Multi-Paxos: preparar resposta com listas
        PhaseOneReply.Builder responseBuilder = PhaseOneReply.newBuilder()
                .setInstance(instance)
                .setServerid(this.state.getServerId())
                .setRequestballot(ballot)
                .setAccepted(accepted)
                .setMaxballot(maxballot);
        
        // Adicionar valores aceitos (para Multi-Paxos)
        if (value != -1) {
            responseBuilder.addValues(value);
            responseBuilder.addValballots(valballot);
        }
        
        // Adicionar outros valores da instância se existirem
        for (Integer cmdId : entry.commandIds) {
            if (cmdId != value && cmdId != -1) {
                responseBuilder.addValues(cmdId);
                responseBuilder.addValballots(entry.writeBallot);
            }
        }
        
        PhaseOneReply response = responseBuilder.build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void phasetwo(PhaseTwoRequest request, StreamObserver<PhaseTwoReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int instance = request.getInstance();
        int ballot = request.getRequestballot();
        int value = request.getValue();
        LOGGER.debug("received phasetwo request (instance={}, ballot={}, value={})", instance, ballot, value);

        PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance);
        boolean accepted = false;
        int maxballot = ballot;

        if (ballot >= this.state.getCurrentBallot()) {
            accepted = true;
            entry.commandId = value;
            entry.writeBallot = ballot;
            this.state.setCurrentBallot(ballot);
        } else {
            maxballot = this.state.getCurrentBallot();
        }
        LOGGER.debug("phasetwo reply: instance={}, maxballot={}, accepted={}, value={}, ballot={}", instance,
                maxballot, accepted, value, ballot);

        PhaseTwoReply response = PhaseTwoReply.newBuilder()
                .setInstance(instance)
                .setServerid(this.state.getServerId())
                .setAccepted(accepted)
                .setRequestballot(ballot)
                .setMaxballot(maxballot)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();

        // Notify learners
        if (accepted == true) {
            this.executor.submit(() -> {
                List<Integer> learners = this.state.getScheduler().learners(ballot);
                LearnRequest learnRequest = LearnRequest.newBuilder()
                        .setInstance(instance)
                        .setBallot(ballot)
                        .setValue(value)
                        .build();
                List<LearnReply> responses = new ArrayList<>();
                GenericResponseCollector<LearnReply> collector = new GenericResponseCollector<>(responses,
                        learners.size());
                for (int learner : learners) {
                    CollectorStreamObserver<LearnReply> observer = new CollectorStreamObserver<>(collector);
                    this.state.getPaxosStub(learner).learn(learnRequest, observer);
                }
            });
        }
    }

    @Override
    public void learn(LearnRequest request, StreamObserver<LearnReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int instance = request.getInstance();
        int ballot = request.getBallot();
        int value = request.getValue();
        LOGGER.debug("received learn request (instance={}, value={}, ballot={})", instance, ballot, value);

        synchronized (this) {
            PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance);
            if (!entry.decided) { // ignore if already decided
                this.state.setCurrentBallot(ballot);
                if (ballot == entry.acceptBallot) {
                    entry.numAccepts++;
                    if (entry.numAccepts >= this.state.getScheduler().quorum(ballot)) {
                        LOGGER.debug("quorum reached for instance {} with {} accepts, deciding value {} in ballot {}",
                                instance, entry.numAccepts, entry.commandId, entry.acceptBallot);
                        this.state.updateCompletedBallot(ballot);
                        entry.decided = true;
                        this.mainLoop.wakeup();
                    }
                } else if (ballot > entry.acceptBallot) {
                    entry.commandId = value;
                    entry.acceptBallot = ballot;
                    entry.numAccepts = 1;
                }
            }
        }

        LearnReply response = LearnReply.newBuilder()
                .setInstance(instance)
                .setBallot(ballot)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
