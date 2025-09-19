package didameetings.util;

import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;

public class PhaseOneProcessor extends GenericResponseProcessor<PhaseOneReply> {

    private final int quorum;

    private boolean accepted = false; // whether the prepare was accepted by a majority
    private int value = -1; // accepted value
    private int valballot = -1; // last successful WRITE
    private int maxballot = -1; // highest promised ballot (READ)
    private int responses = 0;
    private int promises = 0; // number of accepted responses

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

    public int getPromises() {
        return this.promises;
    }

    @Override
    public synchronized boolean onNext(List<PhaseOneReply> allResponses, PhaseOneReply lastResponse) {
        this.responses++;

        DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor received response " + this.responses + " from server " + lastResponse.getServerid() + 
                         " for ballot " + lastResponse.getRequestballot() + " (accepted=" + lastResponse.getAccepted() + 
                         ", value=" + lastResponse.getValue() + ", valballot=" + lastResponse.getValballot() + 
                         ", maxballot=" + lastResponse.getMaxballot() + ")");

        // track the highest promised ballot seen from any reply (accept or reject).
        this.maxballot = Math.max(this.maxballot, lastResponse.getMaxballot());

        if (lastResponse.getAccepted()) {
            this.promises++;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor PROMISE received from server " + lastResponse.getServerid() + 
                             " (promises=" + this.promises + "/" + this.quorum + ")");

            // if the acceptor has already accepted a value, and its valballot is higher than any other seen so far, update the value to propose in phase 2.
            // in short, we some of the replies have a previously accepted value, we should pick the one with the highest valballot.
            if (lastResponse.getValballot() > this.valballot) {
                this.valballot = lastResponse.getValballot();
                this.value = lastResponse.getValue();
                DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor updated value to " + this.value + " from valballot " + this.valballot);
            }
        } else {
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor REJECTION received from server " + lastResponse.getServerid() + 
                             " (rejections=" + (this.responses - this.promises) + "/" + this.quorum + ")");
        }

        // early stop when a majority is reached
        if (this.promises >= this.quorum) {
            this.accepted = true;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor MAJORITY PROMISES reached (" + this.promises + "/" + this.quorum + ") - Phase 1 SUCCESS");
            return true;
        }

        // early stop when a majority is impossible
        if (this.responses - this.promises >= this.quorum) {
            this.accepted = false;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor MAJORITY REJECTIONS reached (" + (this.responses - this.promises) + "/" + this.quorum + ") - Phase 1 FAILED");
            return true;
        }

        DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseOneProcessor continuing to collect responses (promises=" + this.promises + 
                         ", rejections=" + (this.responses - this.promises) + ", total=" + this.responses + ")");
        return false;

        //this.responses++;
        //this.maxballot = lastResponse.getMaxballot();
        //this.value = lastResponse.getValue();
        //this.valballot = lastResponse.getValballot();

        //return true;
    }
}
