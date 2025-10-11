package didameetings.server;

import didameetings.DidaMeetingsMain.AddReply;
import didameetings.DidaMeetingsMain.AddRequest;
import didameetings.DidaMeetingsMain.CloseReply;
import didameetings.DidaMeetingsMain.CloseRequest;
import didameetings.DidaMeetingsMain.DumpReply;
import didameetings.DidaMeetingsMain.DumpRequest;
import didameetings.DidaMeetingsMain.OpenReply;
import didameetings.DidaMeetingsMain.OpenRequest;
import didameetings.DidaMeetingsMain.TopicReply;
import didameetings.DidaMeetingsMain.TopicRequest;
import didameetings.DidaMeetingsMainServiceGrpc.DidaMeetingsMainServiceImplBase;
import io.grpc.stub.StreamObserver;
import didameetings.util.FancyLogger;
import didameetings.util.Logger;

public class DidaMeetingsMainServiceImpl extends DidaMeetingsMainServiceImplBase {

    private static final Logger LOGGER = new FancyLogger("MainService");

    private final DidaMeetingsServerState state;
    private final MainLoop mainLoop;

    public DidaMeetingsMainServiceImpl(DidaMeetingsServerState state, MainLoop mainLoop) {
        this.state = state;
        this.mainLoop = mainLoop;
    }

    @Override
    public void open(OpenRequest request, StreamObserver<OpenReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int reqid = request.getReqid();
        int mid = request.getMeetingid();
        LOGGER.debug("received open request (reqid: {}, mid: {})", reqid, mid);
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.OPEN, mid);
        boolean result = processCommand(reqid, command);
        OpenReply response = OpenReply.newBuilder()
                .setReqid(reqid)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void add(AddRequest request, StreamObserver<AddReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int reqid = request.getReqid();
        int mid = request.getMeetingid();
        int pid = request.getParticipantid();
        LOGGER.debug("received add request (reqid: {}, mid: {}, pid: {})", reqid, mid, pid);
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.ADD, mid, pid);
        boolean result = processCommand(reqid, command);
        AddReply response = AddReply.newBuilder()
                .setReqid(reqid)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void topic(TopicRequest request, StreamObserver<TopicReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int reqid = request.getReqid();
        int mid = request.getMeetingid();
        int pid = request.getParticipantid();
        int topic = request.getTopicid();
        LOGGER.debug("received topic request (reqid: {}, mid: {}, pid: {}, topic: {})", reqid, mid, pid, topic);
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.TOPIC, mid, pid, topic);
        boolean result = processCommand(reqid, command);
        TopicReply response = TopicReply.newBuilder()
                .setReqid(reqid)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void close(CloseRequest request, StreamObserver<CloseReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        System.out.println("[Main] close " + request);
        int reqid = request.getReqid();
        int mid = request.getMeetingid();
        LOGGER.debug("received close request (reqid: {}, mid: {})", reqid, mid);
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.CLOSE, mid);
        boolean result = processCommand(reqid, command);
        CloseReply response = CloseReply.newBuilder()
                .setReqid(reqid)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void dump(DumpRequest request, StreamObserver<DumpReply> responseObserver) {
        this.state.waitIfFrozen();
        this.state.randomDelay();
        int reqid = request.getReqid();
        LOGGER.debug("received dump request (reqid: {})", reqid);
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.DUMP);
        boolean result = processCommand(reqid, command);
        DumpReply response = DumpReply.newBuilder()
                .setReqid(reqid)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private boolean processCommand(int requestId, DidaMeetingsCommand command) {
        RequestRecord request = new RequestRecord(requestId, command);
        if (command.action() == DidaMeetingsAction.TOPIC) {
            this.state.getRequestHistory().addToTopicQueue(request);
        } else {
            this.state.getRequestHistory().addToPending(requestId, request);
        }
        this.mainLoop.wakeup();
        return request.waitForResponse();
    }
}
