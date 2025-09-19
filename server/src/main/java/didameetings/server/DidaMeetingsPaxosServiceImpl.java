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
import didameetings.util.DebugPrinter;
import didameetings.util.GenericResponseCollector;
import io.grpc.stub.StreamObserver;

public class DidaMeetingsPaxosServiceImpl extends DidaMeetingsPaxosServiceImplBase {

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

        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " received Phase 1 Prepare for instance " + instance + " with ballot " + ballot);
        
        PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance, ballot);
        boolean accepted = false;
        int value = entry.commandId;
        int valballot = entry.writeBallot;
        
        int currentBallot = this.state.getCurrentBallot();
        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " current ballot: " + currentBallot + ", requested ballot: " + ballot);
        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " entry readBallot: " + entry.readBallot + ", writeBallot: " + entry.writeBallot + ", commandId: " + entry.commandId);
        
        if (ballot >= this.state.getCurrentBallot()) {
            accepted = true;
            this.state.setCurrentBallot(ballot);
            entry.readBallot = ballot;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " PROMISED to ignore ballots <= " + ballot + " (accepted=true)");
        } else {
            DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " REJECTED prepare - ballot " + ballot + " < current ballot " + currentBallot + " (accepted=false)");
        }
        int maxballot = this.state.getCurrentBallot();

        PhaseOneReply response = PhaseOneReply.newBuilder()
                .setInstance(instance)
                .setServerid(this.state.getServerId())
                .setRequestballot(ballot)
                .setAccepted(accepted)
                .setValue(value)
                .setValballot(valballot)
                .setMaxballot(maxballot)
                .build();
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
        
        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " received Phase 2 Accept for instance " + instance + " with ballot " + ballot + " and value " + value);
        
        PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance);
        boolean accepted = false;
        int maxballot = ballot;

        int currentBallot = this.state.getCurrentBallot();
        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " current ballot: " + currentBallot + ", requested ballot: " + ballot);
        DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " entry readBallot: " + entry.readBallot + ", writeBallot: " + entry.writeBallot + ", commandId: " + entry.commandId);

        if (ballot >= this.state.getCurrentBallot()) {
            accepted = true;
            entry.commandId = value;
            entry.writeBallot = ballot;
            this.state.setCurrentBallot(ballot);
            DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " ACCEPTED value " + value + " for ballot " + ballot + " (accepted=true)");
        } else {
            maxballot = this.state.getCurrentBallot();
            DebugPrinter.debugPrint("[PAXOS-DEBUG] Server " + this.state.getServerId() + " REJECTED accept - ballot " + ballot + " < current ballot " + currentBallot + " (accepted=false)");
        }

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

        synchronized (this) {
            PaxosInstance entry = this.state.getPaxosLog().testAndSetEntry(instance);
            this.state.setCurrentBallot(ballot);
            if (ballot == entry.acceptBallot) {
                entry.numAccepts++;
                if (entry.numAccepts >= this.state.getScheduler().quorum(ballot)) {
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

        LearnReply response = LearnReply.newBuilder()
                .setInstance(instance)
                .setBallot(ballot)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
