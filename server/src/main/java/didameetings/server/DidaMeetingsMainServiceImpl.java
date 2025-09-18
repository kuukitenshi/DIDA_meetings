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

public class DidaMeetingsMainServiceImpl extends DidaMeetingsMainServiceImplBase {

    private final DidaMeetingsServerState state;
    private final MainLoop mainLoop;

    public DidaMeetingsMainServiceImpl(DidaMeetingsServerState state, MainLoop mainLoop) {
        this.state = state;
        this.mainLoop = mainLoop;
    }

    @Override
    public void open(OpenRequest request, StreamObserver<OpenReply> responseObserver) {
        System.out.println("[Main] open " + request);
        int requestId = request.getReqid();
        int mid = request.getMeetingid();
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.OPEN, mid);
        boolean result = processCommand(requestId, command);
        OpenReply response = OpenReply.newBuilder()
                .setReqid(requestId)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void add(AddRequest request, StreamObserver<AddReply> responseObserver) {
        System.out.println("[Main] add " + request);
        int requestId = request.getReqid();
        int mid = request.getMeetingid();
        int pid = request.getParticipantid();
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.ADD, mid, pid);
        boolean result = processCommand(requestId, command);
        AddReply response = AddReply.newBuilder()
                .setReqid(requestId)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void topic(TopicRequest request, StreamObserver<TopicReply> responseObserver) {
        System.out.println("[Main] topic " + request);
        int requestId = request.getReqid();
        int mid = request.getMeetingid();
        int pid = request.getParticipantid();
        int topic = request.getTopicid();
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.TOPIC, mid, pid, topic);
        boolean result = processCommand(requestId, command);
        TopicReply response = TopicReply.newBuilder()
                .setReqid(requestId)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void close(CloseRequest request, StreamObserver<CloseReply> responseObserver) {
        System.out.println("[Main] close " + request);
        int requestId = request.getReqid();
        int meetingId = request.getMeetingid();
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.CLOSE, meetingId);
        boolean result = processCommand(requestId, command);
        CloseReply response = CloseReply.newBuilder()
                .setReqid(requestId)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void dump(DumpRequest request, StreamObserver<DumpReply> responseObserver) {
        System.out.println("[Main] dump " + request);
        int requestId = request.getReqid();
        DidaMeetingsCommand command = new DidaMeetingsCommand(DidaMeetingsAction.DUMP);
        boolean result = processCommand(requestId, command);
        DumpReply response = DumpReply.newBuilder()
                .setReqid(requestId)
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private boolean processCommand(int requestId, DidaMeetingsCommand command) {
        RequestRecord request = new RequestRecord(requestId, command);
        this.state.getRequestHistory().addToPending(requestId, request);
        this.mainLoop.wakeup();
        return request.waitForResponse();
    }
}
