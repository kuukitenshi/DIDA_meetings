package didameetings.configs;

import java.util.List;

public interface Schedule {

    List<Integer> learners(int ballot);

    List<Integer> acceptors(int ballot);

    List<Integer> acceptorsinrange(int low_ballot, int high_ballot);

    boolean isacceptor(int node, int ballot);

    List<Integer> allparticipants();

    List<Integer> allparticipantsinballot(int ballot);

    int leader(int ballot);

    int quorum(int ballot);
}
