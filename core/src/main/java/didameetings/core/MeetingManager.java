package didameetings.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeetingManager {

    private Map<Integer, Meeting> meetings = new HashMap<>();

    public boolean open(int mid, int max) {
        if (this.meetings.containsKey(mid)) {
            return false;
        }
        Meeting meeting = new Meeting(mid, max);
        this.meetings.put(mid, meeting);
        return true;
    }

    public boolean add(int mid, int pid) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return false;
        }
        return meeting.add(pid);
    }

    public boolean setTopic(int mid, int pid, int topic) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return false;
        }
        return meeting.setTopic(pid, topic);
    }

    public boolean addAndClose(Integer mid, Integer pid) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return false;
        }
        if (!meeting.add(pid)) {
            return false;
        }
        if (meeting.size() == meeting.max()) {
            meeting.close();
        }
        return true;
    }

    public boolean close(int mid) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return false;
        }
        meeting.close();
        return true;
    }

    public List<Integer> participantsWithTopic(int mid) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return null;
        }
        return meeting.participantsWithTopic();
    }

    public List<Integer> participantsWithoutTopic(int mid) {
        Meeting meeting = this.meetings.get(mid);
        if (meeting == null) {
            return null;
        }
        return meeting.participantsWithoutTopic();
    }

    public void dump() {
        List<Meeting> closedMeetings = this.meetings.values().stream().filter(m -> m.isClosed()).toList();
        List<Meeting> openMeetings = this.meetings.values().stream().filter(m -> !m.isClosed()).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("\n ----------- Open meetings ----------- \n");
        dumpMeetings(openMeetings, sb);
        sb.append("\n ----------- Closed meetings ----------- \n");
        dumpMeetings(closedMeetings, sb);
        sb.append("\n -----------    done     ----------- \n");
    }

    private void dumpMeetings(List<Meeting> meetings, StringBuilder sb) {
        for (Meeting m : meetings) {
            sb.append("\n\t Meeting " + m.getId());
            sb.append("\n\t\t with topic: ");
            for (int pid : m.participantsWithTopic()) {
                int topic = m.getTopic(pid);
                sb.append(String.format("\n\t\t\t(%s,%s) ", pid, topic));
            }
            sb.append("\n\t\t without topic: ");
            for (int pid : m.participantsWithoutTopic()) {
                sb.append(String.format("\n\t\t\t(%s) ", pid));
            }
        }
    }
}
