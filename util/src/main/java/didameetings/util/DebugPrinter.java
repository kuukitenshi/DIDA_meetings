package didameetings.util;

public class DebugPrinter {
    private static boolean debugEnabled = true;
    
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
    
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    public static void debugPrint(String message) {
        if (debugEnabled) {
            System.out.println(message);
        }
    }
    
    public static void debugPrint(String format, Object... args) {
        if (debugEnabled) {
            System.out.println(String.format(format, args));
        }
    }
}
