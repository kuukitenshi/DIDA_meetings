package didameetings.configs;

import java.util.List;
import java.util.Arrays;

public class ScheduleB implements Schedule {

    private final List<Integer> allParticipants = Arrays.asList(0, 1, 2, 3, 4, 5);
    private final List<Integer> allLearners = allParticipants;
    private final List<Integer> acceptorsStart = Arrays.asList(0, 1, 2);
    private final List<Integer> acceptorsEnd = Arrays.asList(1, 2, 3, 4, 5);
    private final List<Integer> acceptorsAll = allParticipants;

    public List<Integer> learners(int ballot) {
        return this.allLearners;
    }

    public List<Integer> acceptors(int ballot) {
        if (ballot < 2) {
            return this.acceptorsStart;
        }
        return this.acceptorsEnd;
    }

    public List<Integer> acceptorsinrange(int low_ballot, int high_ballot) {
        if (high_ballot < 2) {
            return this.acceptorsStart;
        }
        if (low_ballot >= 2) {
            return this.acceptorsEnd;
        }
        return this.acceptorsAll;
    }

    public List<Integer> allparticipantsinballot(int ballot) {
        return this.allParticipants;
    }

    public List<Integer> allparticipants() {
        return this.allParticipants;
    }

    public boolean isacceptor(int node, int ballot) {
        if (ballot < 2) {
            return this.acceptorsStart.contains(node);
        }
        return this.acceptorsEnd.contains(node);
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
        return ballot < 2 ? 2 : 3;
    }
}
