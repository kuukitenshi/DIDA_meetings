package didameetings.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;
import didameetings.DidaMeetingsPaxos.WrittenValue;

public class PhaseOneProcessor extends GenericResponseProcessor<PhaseOneReply> {

    private static final Logger LOGGER = new FancyLogger("PhaseOneProcessor");

    private final int quorum;
    private final Map<Integer, WrittenValue> values = new HashMap<>();

    private boolean accepted = true;
    private int maxballot = -1;
    private int responses = 0;

    public PhaseOneProcessor(int quorum) {
        this.quorum = quorum;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public int getMaxballot() {
        return this.maxballot;
    }

    public Map<Integer, WrittenValue> getWrittenValues() {
        // replace duplicates with NOPs
        Set<Integer> seenRequests = new HashSet<>();
        Map<Integer, WrittenValue> nopMap = new HashMap<>();
        this.values.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey())) // sort by instances in reverse
                .forEach(e -> {
                    int reqid = e.getValue().getValue();
                    if (!seenRequests.contains(reqid)) {
                        nopMap.put(e.getKey(), e.getValue());
                        seenRequests.add(reqid);
                    } else {
                        WrittenValue nop = WrittenValue.newBuilder(e.getValue()).setValue(-2).build();
                        nopMap.put(e.getKey(), nop);
                    }
                });
        return nopMap;
    }

    @Override
    public synchronized boolean onNext(List<PhaseOneReply> allResponses, PhaseOneReply lastResponse) {
        this.responses++;
        logResponse(lastResponse);

        if (!lastResponse.getAccepted()) {
            this.accepted = false;
            if (lastResponse.getMaxballot() > this.maxballot) {
                this.maxballot = lastResponse.getMaxballot();
            }
            return true;
        }

        for (WrittenValue value : lastResponse.getValuesList()) {
            int instance = value.getInstance();
            int ballot = value.getBallot();
            this.values.putIfAbsent(instance, value);
            if (ballot > this.values.get(instance).getBallot()) {
                this.values.put(instance, value);
            }
        }
        return this.responses >= this.quorum;
    }

    private void logResponse(PhaseOneReply response) {
        boolean accepted = response.getAccepted();
        int maxballot = response.getMaxballot();
        Stream<String> valuesStream = response.getValuesList().stream()
                .map(value -> String.format("{instance=%s, value=%s, ballot=%s}", value.getInstance(), value.getValue(),
                        value.getBallot()));
        String values = "[" + String.join(", ", valuesStream.toList()) + "]";
        LOGGER.debug("received reply {}/{} (acccepted={}, maxballot={}, values={})", this.responses, this.quorum,
                accepted, maxballot, values);
    }
}
