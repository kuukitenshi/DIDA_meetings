package didameetings.server;

public class PaxosInstance {

    public int instanceId = -1;
    public int commandId = -1;
    public int readBallot = -1;
    public int writeBallot = -1;
    public int acceptBallot = -1;
    public int numAccepts = 0;
    public boolean decided = false;
    public boolean valueLocked = false;

    public PaxosInstance(int id) {
        this(id, -1);
    }

    public PaxosInstance(int id, int ballot) {
        this.instanceId = id;
        this.readBallot = ballot;
    }
}
