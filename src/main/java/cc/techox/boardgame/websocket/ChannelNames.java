package cc.techox.boardgame.websocket;

public final class ChannelNames {
    private ChannelNames() {}

    public static String room(String gameCode, Long roomId) {
        String g = gameCode == null ? "" : gameCode.toLowerCase();
        return "room:" + g + ":" + roomId;
    }

    public static String match(String gameCode, Long matchId) {
        String g = gameCode == null ? "" : gameCode.toLowerCase();
        return "match:" + g + ":" + matchId;
    }
}