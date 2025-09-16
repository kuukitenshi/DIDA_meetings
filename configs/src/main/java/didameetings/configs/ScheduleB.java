package didameetings.configs;

import java.util.List;
import java.util.Arrays;

public class ScheduleB implements Schedule {

    private List<Integer> procs = Arrays.asList(0, 1, 2, 3, 4, 5);
    private List<Integer> startingAcceptors = Arrays.asList(0, 1, 2);
    private List<Integer> endingAcceptors = Arrays.asList(1, 2, 3, 4, 5);

    public ScheduleB() {
    }

    public List<Integer> learners(int ballot) {
        return this.procs;
    }

    public List<Integer> acceptors(int ballot) {
        if (ballot < 2) {
            return this.startingAcceptors;
        }
        return this.endingAcceptors;
    }

    public List<Integer> acceptorsinrange(int low_ballot, int high_ballot) {
        if (high_ballot < 2) {
            return this.startingAcceptors;
        }
        if (low_ballot >= 2) {
            return this.endingAcceptors;
        }
        return this.procs;
    }

    public List<Integer> allparticipantsinballot(int ballot) {
        return this.procs;
    }

    public List<Integer> allparticipants() {
        return this.procs;
    }

    public boolean isacceptor(int node, int ballot) {
        if (ballot < 2) {
            return this.startingAcceptors.contains(node);
        }
        return this.endingAcceptors.contains(node);
    }

    public int leader(int ballot) {
        if (ballot < 2) {
            return ballot;
        }
        if (ballot < 5) {
            return ballot + 1;
        }
        return 5;
    }

    public int quorum(int ballot) {
        if (ballot < 2) {
            return 2;
        }
        return 3;
    }
}
