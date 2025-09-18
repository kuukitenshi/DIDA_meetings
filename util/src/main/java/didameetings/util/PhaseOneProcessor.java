package didameetings.util;

import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;

public class PhaseOneProcessor extends GenericResponseProcessor<PhaseOneReply> {

    private final int quorum;

    private boolean accepted = true;
    private int value = -1;
    private int valballot = -1;
    private int maxballot = -1;
    private int responses = 0;

    public PhaseOneProcessor(int quorum) {
        this.quorum = quorum;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public int getValue() {
        return this.value;
    }

    public int getValballot() {
        return this.valballot;
    }

    public int getMaxballot() {
        return this.maxballot;
    }

    @Override
    public synchronized boolean onNext(List<PhaseOneReply> allResponses, PhaseOneReply lastResponse) {
        this.responses++;
        if (!lastResponse.getAccepted()) {
            this.accepted = false;
            if (lastResponse.getMaxballot() > this.maxballot) {
                this.maxballot = lastResponse.getMaxballot();
            }
            if (lastResponse.getValue() > this.value) {
                this.value = lastResponse.getValue();
                this.valballot = lastResponse.getValballot();
            }
            return true;
        }
        return this.responses >= this.quorum;
    }
}
