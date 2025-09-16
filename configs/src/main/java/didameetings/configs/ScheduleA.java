package didameetings.configs;

import java.util.Arrays;
import java.util.List;

public class ScheduleA implements Schedule {

    private List<Integer> procs = Arrays.asList(0, 1, 2);

    public List<Integer> learners(int ballot) {
        return this.procs;
    }

    public List<Integer> acceptors(int ballot) {
        return this.procs;
    }

    public boolean isacceptor(int node, int ballot) {
        return this.procs.contains(node);
    }

    public List<Integer> acceptorsinrange(int low_ballot, int high_ballot) {
        return this.procs;
    }

    public List<Integer> allparticipantsinballot(int ballot) {
        return this.procs;
    }

    public List<Integer> allparticipants() {
        return this.procs;
    }

    public int leader(int ballot) {
        return ballot % 3;
    }

    public int quorum(int ballot) {
        return 2;
    }
}
