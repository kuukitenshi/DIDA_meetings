package didameetings.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import didameetings.DidaMeetingsMainServiceGrpc;
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
import didameetings.DidaMeetingsMainServiceGrpc.DidaMeetingsMainServiceStub;
import didameetings.configs.ConfigurationScheduler;
import didameetings.util.CollectorStreamObserver;
import didameetings.util.GenericResponseCollector;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class AppCommunicator {

    private final ManagedChannel[] channels;
    private final DidaMeetingsMainServiceStub[] stubs;
    private final int clientId;
    private final ConfigurationScheduler scheduler;
    private int sequenceNumber = 0;

    public AppCommunicator(CliArgs args) {
        this.scheduler = args.scheduler();
        int nodeCount = this.scheduler.allparticipants().size();
        this.clientId = args.clientId();
        this.channels = new ManagedChannel[nodeCount];
        this.stubs = new DidaMeetingsMainServiceStub[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            int port = args.port() + i;
            this.channels[i] = ManagedChannelBuilder.forAddress(args.host(),
                    port).build();
            this.stubs[i] = DidaMeetingsMainServiceGrpc.newStub(this.channels[i]);
        }
    }

    public void shutdown() {
        for (ManagedChannel channel : this.channels) {
            channel.shutdownNow();
        }
    }

    public Optional<OpenReply> open(int mid) {
        this.sequenceNumber++;
        int reqid = this.sequenceNumber * 100 + this.clientId;
        OpenRequest request = OpenRequest.newBuilder()
                .setReqid(reqid)
                .setMeetingid(mid)
                .build();
        return sendRequest(request, stub -> stub::open);
    }

    public Optional<CloseReply> close(int mid) {
        this.sequenceNumber++;
        int reqid = this.sequenceNumber * 100 + this.clientId;
        CloseRequest request = CloseRequest.newBuilder()
                .setReqid(reqid)
                .setMeetingid(mid)
                .build();
        return sendRequest(request, stub -> stub::close);
    }

    public Optional<TopicReply> topic(int mid, int pid, int topic) {
        this.sequenceNumber++;
        int reqid = this.sequenceNumber * 100 + this.clientId;
        TopicRequest request = TopicRequest.newBuilder()
                .setReqid(reqid)
                .setMeetingid(mid)
                .setParticipantid(pid)
                .setTopicid(topic)
                .build();
        return sendRequest(request, stub -> stub::topic);

    }

    public Optional<AddReply> add(int mid, int pid) {
        this.sequenceNumber++;
        int reqid = this.sequenceNumber * 100 + this.clientId;
        AddRequest request = AddRequest.newBuilder()
                .setReqid(reqid)
                .setMeetingid(mid)
                .setParticipantid(pid)
                .build();
        return sendRequest(request, stub -> stub::add);

    }

    public Optional<DumpReply> show() {
        this.sequenceNumber++;
        int reqid = this.sequenceNumber * 100 + this.clientId;
        DumpRequest request = DumpRequest.newBuilder()
                .setReqid(reqid)
                .build();
        return sendRequest(request, stub -> stub::dump);
    }

    private <T, U> Optional<U> sendRequest(T request,
            Function<DidaMeetingsMainServiceStub, BiConsumer<T, StreamObserver<U>>> func) {
        List<U> responses = new ArrayList<>();
        GenericResponseCollector<U> collector = new GenericResponseCollector<>(responses, this.channels.length);
        for (DidaMeetingsMainServiceStub stub : this.stubs) {
            CollectorStreamObserver<U> observer = new CollectorStreamObserver<>(collector);
            BiConsumer<T, StreamObserver<U>> consumer = func.apply(stub);
            consumer.accept(request, observer);
        }
        collector.waitForQuorum(1);
        return responses.stream().findFirst();
    }
}
