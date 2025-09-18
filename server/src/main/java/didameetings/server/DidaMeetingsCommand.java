package didameetings.server;

enum DidaMeetingsAction {
    OPEN, ADD, TOPIC, CLOSE, DUMP
};

public record DidaMeetingsCommand(DidaMeetingsAction action, int meetingId, int participantId, int topicId) {

    public DidaMeetingsCommand(DidaMeetingsAction action) {
        this(action, 0, 0, -1);
    }

    public DidaMeetingsCommand(DidaMeetingsAction action, int meetingId) {
        this(action, meetingId, 0, -1);
    }

    public DidaMeetingsCommand(DidaMeetingsAction action, int meetingId, int participantId) {
        this(action, meetingId, participantId, -1);
    }
}
