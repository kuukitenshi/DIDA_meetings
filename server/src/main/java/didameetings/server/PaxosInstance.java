package didameetings.server;

public class PaxosInstance {

    public int instanceId;
    public int commandId;
    public int readBallot;
    public int writeBallot;
    public int acceptBallot;
    public int numAccepts;
    public boolean decided;
    public boolean valueLocked;

    public PaxosInstance() {
        this(0, -1);
    }

    public PaxosInstance(int id) {
        this(id, -1);
    }

    public PaxosInstance(int id, int ballot) {
        this.instanceId = id;
        this.commandId = 0;
        this.readBallot = ballot;
        this.writeBallot = -1;
        this.acceptBallot = -1;
        this.numAccepts = 0;
        this.decided = false;
        this.valueLocked = false;
    }
}
