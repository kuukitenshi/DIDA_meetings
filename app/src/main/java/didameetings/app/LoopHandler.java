package didameetings.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import didameetings.core.Meeting;

public class LoopHandler {

    private static final int OPEN_THRESHOLD = 5;
    private static final int OPEN_TARGET = 10;
    private static final int ACTION_PROBABILITY_RANGE = 4;
    private static final int MAX_PARTICIPANTS = 10;

    private final Random random = new Random();
    private final AppCommunicator communicator;
    private final AppOptions options;

    public LoopHandler(AppCommunicator communicator, AppOptions options) {
        this.communicator = communicator;
        this.options = options;
    }

    public void run() {
        System.out.println("Starting loop with options:\n" + this.options);
        Map<Integer, Meeting> openMeetings = new HashMap<>();
        Map<Integer, Meeting> closedMeetings = new HashMap<>();
        for (int i = 0; i < this.options.loopLength; i++) {
            int numOpen = openMeetings.size();
            int numClosed = closedMeetings.size();
            if (numOpen < OPEN_THRESHOLD) {
                int mid = this.random.nextInt(this.options.meetingRange);
                this.communicator.open(mid).ifPresent(reply -> {
                    System.out.printf("call OPEN(%s) -> %s%n", mid, reply.getResult());
                    if (reply.getResult()) {
                        Meeting meeting = new Meeting(mid, MAX_PARTICIPANTS);
                        openMeetings.put(mid, meeting);
                    }
                });
                continue;
            }

            int action = this.random.nextInt(ACTION_PROBABILITY_RANGE);
            if (action < (ACTION_PROBABILITY_RANGE * 0.25)) {
                action = this.random.nextInt(ACTION_PROBABILITY_RANGE);
                if ((numOpen < OPEN_TARGET && action < ACTION_PROBABILITY_RANGE * 0.25)
                        || (numOpen >= OPEN_TARGET && action < ACTION_PROBABILITY_RANGE * 0.25)) {
                    int mid = this.random.nextInt(this.options.meetingRange);
                    this.communicator.open(mid).ifPresent(reply -> {
                        System.out.printf("call OPEN(%s) -> %s%n", mid, reply.getResult());
                        if (reply.getResult()) {
                            openMeetings.put(mid, new Meeting(mid, MAX_PARTICIPANTS));
                        }
                    });
                } else if (numOpen > 0) {
                    int selection = this.random.nextInt(numOpen);
                    openMeetings.keySet().stream().skip(selection).findFirst().ifPresent(mid -> {
                        this.communicator.close(mid).ifPresent(reply -> {
                            System.out.printf("call CLOSE(%s) -> %s%n", mid, reply.getResult());
                            if (reply.getResult()) {
                                Meeting m = openMeetings.remove(mid);
                                closedMeetings.put(mid, m);
                            }
                        });
                    });
                }
            } else if (action < (ACTION_PROBABILITY_RANGE * 0.50)) {
                int openOrClose = this.random.nextInt(2);
                Meeting meeting = null;
                if (openOrClose == 0 && numOpen > 0) {
                    int selection = this.random.nextInt(numOpen);
                    Optional<Integer> midOpt = openMeetings.keySet().stream().skip(selection).findFirst();
                    if (midOpt.isPresent()) {
                        meeting = openMeetings.get(midOpt.get());
                    }
                } else if (openOrClose == 1 && numClosed > 0) {
                    int selection = this.random.nextInt(numClosed);
                    Optional<Integer> midOpt = closedMeetings.keySet().stream().skip(selection).findFirst();
                    if (midOpt.isPresent()) {
                        meeting = closedMeetings.get(midOpt.get());
                    }
                }
                if (meeting != null) {
                    List<Integer> participants = meeting.participantsWithoutTopic();
                    if (participants.size() > 0) {
                        int selection = this.random.nextInt(participants.size());
                        int mid = meeting.getId();
                        int pid = participants.get(selection);
                        int topic = this.random.nextInt(this.options.topicRange);
                        meeting.setTopic(pid, topic);
                        this.communicator.topic(mid, pid, topic).ifPresent(reply -> {
                            System.out.printf("call TOPIC(%s, %s, %s) -> %s%n", mid, pid, topic, reply.getResult());
                        });
                    } else {
                        System.err.println("no participants to execute TOPIC :(");
                    }
                } else {
                    System.err.println("no meetings to execute TOPIC");
                }
            } else if (numOpen > 0) {
                int selection = this.random.nextInt(numOpen);
                openMeetings.keySet().stream().skip(selection).findFirst().ifPresent(mid -> {
                    int pid = this.random.nextInt(this.options.participantRange);
                    this.communicator.add(mid, pid).ifPresent(reply -> {
                        System.out.printf("call ADD(%s, %s) -> %s%n", mid, pid, reply.getResult());
                        if (reply.getResult()) {
                            openMeetings.get(mid).add(pid);
                        }
                    });
                });
            }
        }
    }
}
