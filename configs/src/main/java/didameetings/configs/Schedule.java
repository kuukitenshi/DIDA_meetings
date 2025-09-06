package didameetings.configs;

import java.util.*;


public interface Schedule {

    List<Integer> learners(int ballot);
    
    List<Integer> acceptors(int ballot);

    List<Integer> acceptorsinrange(int low_ballot, int high_ballot);

    boolean isacceptor(int node, int ballot);

    List<Integer> allparticipants ();

    List<Integer> allparticipantsinballot (int ballot);

    Integer leader (int ballot);

    Integer quorum (int ballot);
}
