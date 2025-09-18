package didameetings.server;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PaxosLog {

    private Map<Integer, PaxosInstance> log = new HashMap<>();

    public synchronized int length() {
        return this.log.size();
    }

    public synchronized PaxosInstance getEntry(int position) {
        return this.log.get(position);
    }

    public synchronized PaxosInstance testAndSetEntry(int position) {
        return testAndSetEntry(position, -1);
    }

    public synchronized PaxosInstance testAndSetEntry(int position, int ballot) {
        PaxosInstance entry = this.log.get(position);
        if (entry == null) {
            entry = new PaxosInstance(position, ballot);
            this.log.put(position, entry);
        }
        return entry;
    }

    public Collection<PaxosInstance> entries() {
        return this.log.values();
    }
}
