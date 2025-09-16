package didameetings.configs;

import java.util.List;

public class ConfigurationScheduler {

    private Schedule schedule;

    public ConfigurationScheduler() {
        this('A');
    }

    public ConfigurationScheduler(char option) {
        setSchedule(option);
    }

    public void setSchedule(char option) {
        if (option == 'A')
            schedule = new ScheduleA();
        else if (option == 'B')
            schedule = new ScheduleB();
    }

    public List<Integer> learners(int ballot) {
        return schedule.learners(ballot);
    }

    public List<Integer> acceptors(int ballot) {
        return schedule.acceptors(ballot);
    }

    public List<Integer> acceptorsinrange(int low_ballot, int high_ballot) {
        return schedule.acceptorsinrange(low_ballot, high_ballot);
    }

    public boolean isacceptor(int node, int ballot) {
        return schedule.isacceptor(node, ballot);
    }

    public int leader(int ballot) {
        return schedule.leader(ballot);
    }

    public List<Integer> allparticipantsinballot(int ballot) {
        return schedule.allparticipantsinballot(ballot);
    }

    public List<Integer> allparticipants() {
        return schedule.allparticipants();
    }

    public int quorum(int ballot) {
        return schedule.quorum(ballot);
    }
}
