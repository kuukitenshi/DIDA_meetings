package didameetings.configs;

import java.util.Arrays;
import java.util.List;

public class ScheduleA implements Schedule {

    private final List<Integer> allParticipants = Arrays.asList(0, 1, 2);
    private final List<Integer> allLearners = allParticipants;
    private final List<Integer> allAcceptors = allParticipants;

    public List<Integer> learners(int ballot) {
        return this.allLearners;
    }

    public List<Integer> acceptors(int ballot) {
        return this.allAcceptors;
    }

    public boolean isacceptor(int node, int ballot) {
        return this.allAcceptors.contains(node);
    }

    public List<Integer> acceptorsinrange(int low_ballot, int high_ballot) {
        return this.allAcceptors;
    }

    public List<Integer> allparticipantsinballot(int ballot) {
        return this.allParticipants;
    }

    public List<Integer> allparticipants() {
        return this.allParticipants;
    }

    public int leader(int ballot) {
        return ballot % 3;
    }

    public int quorum(int ballot) {
        return 2;
    }
}
