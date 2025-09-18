package didameetings.util;

import io.grpc.stub.StreamObserver;

public class CollectorStreamObserver<T> implements StreamObserver<T> {

    private final GenericResponseCollector<T> collector;
    private boolean done = false;

    public CollectorStreamObserver(GenericResponseCollector<T> c) {
        this.collector = c;
    }

    @Override
    public void onNext(T value) {
        if (!this.done) {
            collector.addResponse(value);
            this.done = true;
        }
    }

    @Override
    public void onError(Throwable t) {
        if (!this.done) {
            collector.addNoResponse();
            this.done = true;
        }
    }

    @Override
    public void onCompleted() {
        if (!this.done) {
            collector.addNoResponse();
            this.done = true;
        }
    }
}
