package didameetings.util;

import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseTwoReply;

public class PhaseTwoResponseProcessor extends GenericResponseProcessor<PhaseTwoReply> {

    private static final Logger LOGGER = new FancyLogger("PhaseTwoProcessor");

    private final int quorum;
    private boolean accepted = true;
    private int maxballot = 0;
    private int responses = 0;

    public PhaseTwoResponseProcessor(int q) {
        this.quorum = q;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public int getMaxballot() {
        return this.maxballot;
    }

    @Override
    public synchronized boolean onNext(List<PhaseTwoReply> allResponses, PhaseTwoReply lastResponse) {
        this.responses++;
        LOGGER.debug("received reply {}/{} (accepted={}, maxballot={})", this.responses, this.quorum,
                lastResponse.getAccepted(), lastResponse.getMaxballot());
        if (!lastResponse.getAccepted()) {
            this.accepted = false;
            if (lastResponse.getMaxballot() > this.maxballot) {
                this.maxballot = lastResponse.getMaxballot();
            }
            return true;
        }
        return this.responses >= this.quorum;
    }
}
