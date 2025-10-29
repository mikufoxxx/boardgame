package cc.techox.boardgame.game.uno;

import java.util.*;

public class UnoState {
    public static class PlayerState {
        public long userId;
        public List<String> hand = new ArrayList<>(); // UnoCard.code()
        public PlayerState() {}
        public PlayerState(long userId) { this.userId = userId; }
    }

    public List<PlayerState> players = new ArrayList<>();
    public Deque<String> drawPile = new ArrayDeque<>();
    public Deque<String> discardPile = new ArrayDeque<>();
    public int currentIdx = 0; // 当前出牌玩家索引
    public int direction = 1; // 1 顺时针，-1 逆时针
    public int pendingDraw = 0; // 累积抽牌惩罚
    public String forcedColor = null; // WILD / D4 选色
    public boolean started = false;
    public boolean finished = false;
    public Long winnerUserId = null;

    public PlayerState currentPlayer() { return players.get(currentIdx); }

    public int nextIndex(int step) {
        int n = players.size();
        int i = currentIdx;
        for (int k = 0; k < step; k++) {
            i = (i + direction + n) % n;
        }
        return i;
    }
}