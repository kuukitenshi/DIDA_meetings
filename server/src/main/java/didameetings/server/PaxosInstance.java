package didameetings.server;

import java.util.ArrayList;
import java.util.List;

public class PaxosInstance {

    public int instanceId = -1;
    
    public int commandId = -1;
    public int readBallot = -1;
    public int writeBallot = -1;
    public int acceptBallot = -1;
    public int numAccepts = 0;
    public boolean decided = false;
    public boolean valueLocked = false;
    
    public List<Integer> commandIds = new ArrayList<>();
    public boolean needsPhaseTwo = true;

    public PaxosInstance(int id) {
        this(id, -1);
    }

    public PaxosInstance(int id, int ballot) {
        this.instanceId = id;
        this.readBallot = ballot;
    }

    public void addCommand(int commandId) {
        if (!commandIds.contains(commandId)) {
            commandIds.add(commandId);
        }
    }
    
    public int getCommandId() {
        return commandIds.isEmpty() ? commandId : commandIds.get(0);
    }
    
    public void setCommandId(int cmd) {
        this.commandId = cmd;
        if (commandIds.isEmpty()) {
            commandIds.add(cmd);
        } else {
            commandIds.set(0, cmd);
        }
    }
    
    public boolean shouldRunPhaseTwo(int currentBallot, int currentLeader, int previousLeader) {
        return needsPhaseTwo || (previousLeader != -1 && previousLeader != currentLeader);
    }
    
    public void markPhaseTwoExecuted() {
        this.needsPhaseTwo = false;
    }
}
