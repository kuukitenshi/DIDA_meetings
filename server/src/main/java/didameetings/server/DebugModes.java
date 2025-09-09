package didameetings.server;

public class DebugModes {
    public void applyMode(int mode, DidaMeetingsServerState state) {
        switch (mode) {
            case 1: // crash
                System.exit(-1);
                break;
            case 2: // freeze
                if (state != null) 
                    state.setFreeze(true);
                break;
            case 3: // un-freeze
                if (state != null) 
                    state.setFreeze(false);
                break;
            case 4: // slow-mode-on
                if (state != null) {
                    state.setSlowMode(true);
                }
                break;
            case 5: // slow-mode-off
                if (state != null) {
                    state.setSlowMode(false);
                }
                break;
            // TODO: fazer debug mode para testar a coisa dos 2 liders
            default:
                break;
        }
    }
}
