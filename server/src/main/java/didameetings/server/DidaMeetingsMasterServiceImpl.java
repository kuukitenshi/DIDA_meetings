package didameetings.server;

import didameetings.DidaMeetingsMaster.NewBallotReply;
import didameetings.DidaMeetingsMaster.NewBallotRequest;
import didameetings.DidaMeetingsMaster.SetDebugReply;
import didameetings.DidaMeetingsMaster.SetDebugRequest;
import didameetings.DidaMeetingsMasterServiceGrpc.DidaMeetingsMasterServiceImplBase;
import io.grpc.stub.StreamObserver;

public class DidaMeetingsMasterServiceImpl extends DidaMeetingsMasterServiceImplBase {

    private final DidaMeetingsServerState state;
    private final MainLoop mainLoop;

    public DidaMeetingsMasterServiceImpl(DidaMeetingsServerState state, MainLoop mainLoop) {
        this.state = state;
        this.mainLoop = mainLoop;
    }

    @Override
    public void newballot(NewBallotRequest request, StreamObserver<NewBallotReply> responseObserver) {
        int reqId = request.getReqid();
        int newBallot = request.getNewballot();
        int completedBallot = request.getCompletedballot();

        this.state.setCompletedBallot(completedBallot);
        if (newBallot > this.state.getCurrentBallot()) {
            this.state.setCurrentBallot(newBallot);
            this.mainLoop.wakeup();
            completedBallot = this.state.waitForCompletedBallot(newBallot);
        } else {
            completedBallot = this.state.getCompletedBallot();
        }

        NewBallotReply response = NewBallotReply.newBuilder()
                .setReqid(reqId)
                .setCompletedballot(completedBallot)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void setdebug(SetDebugRequest request, StreamObserver<SetDebugReply> responseObserver) {
        this.state.setDebugMode(request.getMode());
        SetDebugReply response = SetDebugReply.newBuilder()
                .setReqid(request.getReqid())
                .setAck(true)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
