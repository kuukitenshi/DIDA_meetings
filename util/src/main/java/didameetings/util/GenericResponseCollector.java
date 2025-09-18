package didameetings.util;

import java.util.List;

public class GenericResponseCollector<T> {

    private final GenericResponseProcessor<T> processor;
    private final List<T> collectedResponses;
    private boolean done = false;
    private int received = 0;
    private int pending;

    public GenericResponseCollector(List<T> responses, int maxResponses) {
        this(responses, maxResponses, null);
    }

    public GenericResponseCollector(List<T> responses, int maxResponses, GenericResponseProcessor<T> processor) {
        this.collectedResponses = responses;
        this.pending = maxResponses;
        this.processor = processor;
    }

    public synchronized void addResponse(T resp) {
        if (!this.done) {
            collectedResponses.add(resp);
            if (this.processor != null)
                this.done = this.processor.onNext(this.collectedResponses, resp);
        }
        this.received++;
        this.pending--;
        if (this.pending == 0) {
            this.done = true;
        }
        notifyAll();
    }

    public synchronized void addNoResponse() {
        this.pending--;
        if (this.pending == 0) {
            this.done = true;
        }
        notifyAll();
    }

    public synchronized void waitForQuorum(int quorum) {
        while (!this.done && this.received < quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        this.done = true;
    }

    public synchronized void waitUntilDone() {
        while (!this.done) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
    }
}
