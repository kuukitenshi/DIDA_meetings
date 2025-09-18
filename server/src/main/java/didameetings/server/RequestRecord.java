package didameetings.server;

import java.util.Optional;

public class RequestRecord {

    private final int id;
    private final DidaMeetingsCommand command;
    private Optional<Boolean> response = Optional.empty();

    public RequestRecord(int id) {
        this(id, null);
    }

    public RequestRecord(int id, DidaMeetingsCommand command) {
        this.id = id;
        this.command = command;
    }

    public int getId() {
        return this.id;
    }

    public DidaMeetingsCommand getCommand() {
        return command;
    }

    public synchronized void setResponse(boolean response) {
        this.response = Optional.of(response);
        this.notifyAll();
    }

    public synchronized boolean waitForResponse() {
        while (this.response.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return this.response.get();
    }
}
