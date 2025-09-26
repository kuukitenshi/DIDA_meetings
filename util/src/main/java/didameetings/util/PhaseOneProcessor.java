package didameetings.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import didameetings.DidaMeetingsPaxos.PhaseOneReply;
import didameetings.DidaMeetingsPaxos.WrittenValue;

public class PhaseOneProcessor extends GenericResponseProcessor<PhaseOneReply> {

    private static final Logger LOGGER = new FancyLogger("PhaseOneProcessor");

    private final int quorum;
    private final Map<Integer, WrittenValue> values = new HashMap<>();
    private final Map<Integer, Integer> valuesInstances = new HashMap<>();

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
        return this.values;
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
                // avoid double value in different instances
                this.valuesInstances.computeIfPresent(value.getValue(), (_, v) -> this.valuesInstances.remove(v));
                this.valuesInstances.put(value.getValue(), instance);
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
