package didameetings.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Meeting {

    private int id;
    private int maxParticipants;
    private boolean closed = false;
    private Map<Integer, Participant> participants = new HashMap<>();

    public Meeting(int id, int max) {
        this.id = id;
        this.maxParticipants = max;
    }

    public int getId() {
        return this.id;
    }

    public int max() {
        return this.maxParticipants;
    }

    public boolean add(int id) {
        if (this.closed || this.participants.size() >= this.maxParticipants || this.participants.containsKey(id)) {
            return false;
        }
        Participant p = new Participant(id);
        this.participants.put(id, p);
        return true;
    }

    public boolean setTopic(int pid, int topic) {
        Participant p = this.participants.get(pid);
        if (p == null) {
            return false;
        }
        p.setTopic(topic);
        return true;
    }

    public int getTopic(int pid) {
        Participant p = this.participants.get(pid);
        return p != null ? p.getTopic() : -1;
    }

    public boolean close() {
        if (!this.closed) {
            this.closed = true;
            return true;
        }
        return false;
    }

    public boolean isClosed() {
        return closed;
    }

    public Participant getParticipant(int id) {
        return this.participants.get(id);
    }

    public List<Integer> participantsWithTopic() {
        return this.participants.values().stream().filter(p -> p.getTopic() != -1).map(p -> p.getId()).toList();
    }

    public List<Integer> participantsWithoutTopic() {
        return this.participants.values().stream().filter(p -> p.getTopic() == -1).map(p -> p.getId()).toList();
    }

    public int size() {
        return this.participants.size();
    }
}
