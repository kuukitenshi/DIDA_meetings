package didameetings.util;

import java.util.List;

import didameetings.DidaMeetingsPaxos.PhaseTwoReply;

public class PhaseTwoResponseProcessor extends GenericResponseProcessor<PhaseTwoReply> {

    private boolean accepted = false;
    private int maxballot = -1;
    private int responses = 0;
    private final int quorum;
    private int accepts = 0;
    private int rejects = 0;
    public boolean ballotAborted = false; // at least one acceptor promised a higher ballot

    public PhaseTwoResponseProcessor(int q) {
        this.quorum = q;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public int getMaxballot() {
        return this.maxballot;
    }

    public int getAccepts() {
        return this.accepts;
    }

    @Override
    public synchronized boolean onNext(List<PhaseTwoReply> allResponses, PhaseTwoReply lastResponse) {
        this.responses++;

        DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor received response " + this.responses + " from server " + lastResponse.getServerid() + 
                         " for ballot " + lastResponse.getRequestballot() + " (accepted=" + lastResponse.getAccepted() + 
                         ", maxballot=" + lastResponse.getMaxballot() + ")");

        // track the highest promised ballot from any reply
        this.maxballot = Math.max(this.maxballot, lastResponse.getMaxballot());

        if (lastResponse.getAccepted()) {
            this.accepts++;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor ACCEPT received from server " + lastResponse.getServerid() + 
                             " (accepts=" + this.accepts + "/" + this.quorum + ")");
        } else {
            this.rejects++;
            this.ballotAborted = true; // at least one acceptor promised a higher ballot
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor REJECT received from server " + lastResponse.getServerid() + 
                             " (rejects=" + this.rejects + "/" + this.quorum + ") - BALLOT ABORTED due to higher ballot " + lastResponse.getMaxballot());
        }

        // early stop on majority accept or reject
        if (this.accepts >= this.quorum) {
            this.accepted = true;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor MAJORITY ACCEPTS reached (" + this.accepts + "/" + this.quorum + ") - Phase 2 SUCCESS");
            return true;
        }
        if (this.rejects >= this.quorum) {
            this.accepted = false;
            DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor MAJORITY REJECTS reached (" + this.rejects + "/" + this.quorum + ") - Phase 2 FAILED");
            return true;
        }

        DebugPrinter.debugPrint("[PAXOS-DEBUG] PhaseTwoResponseProcessor continuing to collect responses (accepts=" + this.accepts + 
                         ", rejects=" + this.rejects + ", total=" + this.responses + ")");
        return false; // keep collecting
    }
}
